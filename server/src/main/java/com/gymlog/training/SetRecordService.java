package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.training.dto.SetRecordRequest;
import com.gymlog.training.dto.SetRecordResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组记录 —— 用户实际做的那一组。
 *
 * <h3>为什么单独一个 Service，而不是塞进 SessionService</h3>
 *
 * <p>两者变化的原因不同：会话生命周期因为「会话有哪些状态」而变，
 * 组记录因为「跟练界面怎么记录」而变。
 * 放在一起的话，改跟练交互要读一整个会话生命周期的代码才敢动手。
 *
 * <p>共用的归属校验与状态守卫直接从 {@link SessionService} 调——
 * 见那边 {@code loadOwned} 的注释：这类检查**只能有一份实现**。
 *
 * <h3>★ 幂等：靠业务键，不靠额外的 UUID</h3>
 *
 * <p>唯一索引 {@code (session_exercise_id, set_number)} 同时承担
 * 数据约束和离线同步的幂等键。客户端重放「记录第 3 组」时走 UPSERT，
 * 重复上传不产生两条（AC-5-1）。
 *
 * <p>不另加 {@code client_key} 的理由：组号本来就是这个动作内的唯一标识，
 * 再加一列只是多一个要维护的东西，而且**重试时那个 UUID 可能被重新生成**，
 * 幂等就失效了。业务键不会。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetRecordService {

    private final SessionService sessionService;
    private final SessionExerciseMapper sessionExerciseMapper;
    private final SetRecordMapper setRecordMapper;

    // ==================================================================
    // 记录一组
    // ==================================================================

    /**
     * 记录 / 覆盖一组。
     *
     * <p><b>UPSERT 而不是「已存在就报错」</b>：离线重试会带着同样的
     * {@code (动作, 组号)} 再来一次，报错会让客户端把它当成失败而继续重试。
     *
     * <p>而且用户现场改主意也是常态——本来填了 60kg，发现状态好改成 65kg，
     * 再点一次保存是最自然的交互。
     */
    @Transactional
    public SetRecordResponse recordSet(Long userId, Long sessionId, Long sessionExerciseId,
                                       Integer setNumber, SetRecordRequest request) {

        WorkoutSession session = sessionService.loadOwned(userId, sessionId);
        sessionService.requireInProgress(session, "记录");

        SessionExercise exercise = loadExercise(sessionId, sessionExerciseId);

        SetRecord existing = findBySetNumber(sessionExerciseId, setNumber);

        SetRecord record = existing == null ? new SetRecord() : existing;
        record.setSessionExerciseId(sessionExerciseId);
        record.setSetNumber(setNumber);
        record.setSetType(request.setType() == null ? SetType.WORKING : request.setType());
        record.setWeight(request.weight());
        record.setReps(request.reps());
        record.setDurationSec(request.durationSec());
        record.setDistanceM(request.distanceM());
        record.setRpe(request.rpe());
        record.setRestActualSec(request.restActualSec());
        record.setNote(request.note());
        record.setCompletedAt(request.completedAt());

        if (existing == null) {
            setRecordMapper.insert(record);
        } else {
            setRecordMapper.updateById(record);
        }

        // 记录完一组，动作的完成状态可能就变了
        SessionExerciseStatus status = refreshExerciseStatus(exercise);

        if (log.isDebugEnabled()) {
            log.debug("记录一组 | userId={} | sessionId={} | exercise={} | set={} | {}kg x {}",
                    userId, sessionId, sessionExerciseId, setNumber,
                    request.weight(), request.reps());
        }

        return progress(exercise, status);
    }

    // ==================================================================
    // 删除一组
    // ==================================================================

    /**
     * 删除一组（M4-D-3：临时删除组）。
     *
     * <p>删除**只影响本次会话**，不碰计划模板（不变量 3）。
     * 用户删掉的那一组，下次练同样的计划还是会有。
     *
     * <p>删完会把动作状态回退：从「已完成」变回「进行中」。
     * 否则界面会显示「5/5 完成」但实际只有 4 条记录。
     */
    @Transactional
    public SetRecordResponse deleteSet(Long userId, Long sessionId, Long sessionExerciseId,
                                       Integer setNumber) {

        WorkoutSession session = sessionService.loadOwned(userId, sessionId);
        sessionService.requireInProgress(session, "记录");

        SessionExercise exercise = loadExercise(sessionId, sessionExerciseId);

        int deleted = setRecordMapper.delete(
                new LambdaQueryWrapper<SetRecord>()
                        .eq(SetRecord::getSessionExerciseId, sessionExerciseId)
                        .eq(SetRecord::getSetNumber, setNumber));

        if (deleted == 0) {
            // 幂等：删一个本来就不存在的组，不是错误。
            // 离线队列里「删除」和「记录」可能乱序到达，
            // 报错会让客户端卡在重试上。
            log.debug("删除不存在的组，忽略 | sessionExerciseId={} | set={}",
                    sessionExerciseId, setNumber);
        }

        SessionExerciseStatus status = refreshExerciseStatus(exercise);
        return progress(exercise, status);
    }

    // ==================================================================
    // 显式改变动作状态
    // ==================================================================

    /**
     * 显式设置动作状态（M4-D-1 跳过当前动作）。
     *
     * <p>和自动推进的区别：这是用户的**明确决定**，
     * 不该被「记录了几组」覆盖掉。所以它只写状态，不重算。
     */
    @Transactional
    public SetRecordResponse updateExerciseStatus(Long userId, Long sessionId,
                                                  Long sessionExerciseId,
                                                  SessionExerciseStatus status) {

        WorkoutSession session = sessionService.loadOwned(userId, sessionId);
        sessionService.requireInProgress(session, "记录");

        SessionExercise exercise = loadExercise(sessionId, sessionExerciseId);

        SessionExercise update = new SessionExercise();
        update.setId(sessionExerciseId);
        update.setStatus(status);
        sessionExerciseMapper.updateById(update);

        log.info("动作状态变更 | userId={} | sessionId={} | exerciseId={} | {} -> {}",
                userId, sessionId, sessionExerciseId, exercise.getStatus(), status);

        return progress(exercise, status);
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 按「记录了几组」重新推导动作状态。
     *
     * <pre>
     *   0 组                    → PENDING（或保持 SKIPPED，见下）
     *   &gt;= 目标组数            → COMPLETED
     *   否则                    → IN_PROGRESS
     * </pre>
     *
     * <p><b>SKIPPED 的例外</b>：用户跳过了一个动作，此时它 0 条记录。
     * 直接按规则推导会把它变回 PENDING，跳过就白跳了。
     * 所以「0 组且当前是 SKIPPED」时保持不变。
     *
     * <p>但一旦真记录了组，就按记录的来——**用户动手了，说明他改主意了**。
     */
    private SessionExerciseStatus refreshExerciseStatus(SessionExercise exercise) {
        int recorded = countRecords(exercise.getId());

        SessionExerciseStatus next;
        if (recorded == 0) {
            next = exercise.getStatus() == SessionExerciseStatus.SKIPPED
                    ? SessionExerciseStatus.SKIPPED
                    : SessionExerciseStatus.PENDING;
        } else if (exercise.getTargetSets() != null
                && exercise.getTargetSets() > 0
                && recorded >= exercise.getTargetSets()) {
            next = SessionExerciseStatus.COMPLETED;
        } else {
            next = SessionExerciseStatus.IN_PROGRESS;
        }

        if (next != exercise.getStatus()) {
            SessionExercise update = new SessionExercise();
            update.setId(exercise.getId());
            update.setStatus(next);
            sessionExerciseMapper.updateById(update);
            exercise.setStatus(next);
        }
        return next;
    }

    private int countRecords(Long sessionExerciseId) {
        Long count = setRecordMapper.selectCount(
                new LambdaQueryWrapper<SetRecord>()
                        .eq(SetRecord::getSessionExerciseId, sessionExerciseId));
        return count == null ? 0 : count.intValue();
    }

    /**
     * 加载动作快照，并确认它属于这个会话。
     *
     * <p>⚠️ <b>必须校验归属</b>：路径里同时有 sessionId 和 sessionExerciseId，
     * 不校验的话，用户可以用自己的 sessionId 配上**别人的** sessionExerciseId，
     * 往别人的训练记录里写数据。这类「两个 id 都在路径里、只校验了其中一个」
     * 是越权漏洞的经典形态。
     */
    private SessionExercise loadExercise(Long sessionId, Long sessionExerciseId) {
        SessionExercise exercise = sessionExerciseMapper.selectById(sessionExerciseId);
        if (exercise == null || !exercise.getSessionId().equals(sessionId)) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND, "该训练中没有这个动作");
        }
        return exercise;
    }

    private SetRecord findBySetNumber(Long sessionExerciseId, Integer setNumber) {
        return setRecordMapper.selectOne(
                new LambdaQueryWrapper<SetRecord>()
                        .eq(SetRecord::getSessionExerciseId, sessionExerciseId)
                        .eq(SetRecord::getSetNumber, setNumber));
    }

    /**
     * 组装「这个动作现在进行到哪了」，供客户端更新界面。
     *
     * <p><b>⚠️ {@code finished} 必须由参数 {@code status} 推导，
     * 不能用 {@code exercise.isFinished()}。</b>
     *
     * <p>因为传进来的实体是**改之前**从库里读出来的，状态还是旧的。
     * 用它的话，「跳过动作」会返回 {@code status=SKIPPED} 但
     * {@code finished=false}——客户端据此就不会跳到下一个动作，
     * 用户点了跳过却停在原地。
     *
     * <p>（这个 bug 是被测试抓出来的：{@code skipExercise} 断言 finished 为 true 时失败了。）
     *
     * <p>从参数推导还顺带消除了「调用方必须先刷新实体」这个隐性约定。
     */
    private SetRecordResponse progress(SessionExercise exercise, SessionExerciseStatus status) {
        return new SetRecordResponse(
                exercise.getId(),
                status.name(),
                status.getDisplayName(),
                countRecords(exercise.getId()),
                exercise.getTargetSets() == null ? 0 : exercise.getTargetSets(),
                status == SessionExerciseStatus.COMPLETED
                        || status == SessionExerciseStatus.SKIPPED);
    }
}
