package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.program.dto.ExpandedWorkout;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SessionDetailResponse;
import com.gymlog.training.dto.SetRecordResponse;
import com.gymlog.training.dto.TodayWorkoutResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 训练会话的生命周期：创建（含快照） → 完成 / 放弃。
 *
 * <h3>创建会话的流程</h3>
 * <pre>
 *   1. 幂等检查   —— clientKey 已存在就直接返回那一条
 *   2. 进行中检查 —— 一个人同时只能练一场
 *   3. 展开处方   —— 复用 TodayWorkoutService，不重算「第几周第几天」
 *   4. 深拷贝     —— 写进 session_exercise / session_set_target
 * </pre>
 *
 * <h3>★ 第 3 步为什么必须复用 TodayWorkoutService</h3>
 *
 * <p>因为「首页显示今天练什么」和「点开始训练实际练什么」
 * <b>必须是同一份结果</b>。
 *
 * <p>如果这里自己重新算一遍周次和训练日，就会出现这种 bug：
 * 首页显示「推日 · 卧推 63kg」，用户点开始，实际进去是「拉日」——
 * 而且两边的代码单独看都是对的，只有对着用才发现。
 *
 * <p>所以这里不重算，直接把 {@code TodayWorkoutService.today()} 的结果
 * 原样快照下来。**"唯一真相来源"不是靠约定，是靠只写一遍。**
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final WorkoutSessionMapper sessionMapper;
    private final SessionExerciseMapper sessionExerciseMapper;
    private final SessionSetTargetMapper sessionSetTargetMapper;
    private final SetRecordMapper setRecordMapper;
    private final TodayWorkoutService todayWorkoutService;

    // ==================================================================
    // 创建
    // ==================================================================

    /**
     * 开始一次训练。
     *
     * <p><b>整个方法在一个事务里</b>——会话和它的快照要么全部成功，
     * 要么全部回滚。半截会话（有 session 没有 exercises）在界面上表现为
     * 「打开训练一片空白」，而且很难排查。
     */
    @Transactional
    public SessionDetailResponse create(Long userId, SessionCreateRequest request) {

        // ---------- 1. 幂等 ----------
        //
        // 离线重试会走到这里。**返回已存在的那条，而不是报错**——
        // 客户端重试本来就不该收到错误，它只是想确保数据到了。
        if (request.clientKey() != null) {
            WorkoutSession existing = findByClientKey(userId, request.clientKey());
            if (existing != null) {
                log.info("会话已存在，返回原纪录 | userId={} | clientKey={} | sessionId={}",
                        userId, request.clientKey(), existing.getId());
                return detail(userId, existing.getId(), true);
            }
        }

        // ---------- 2. 同一用户只能有一个进行中的会话 ----------
        WorkoutSession active = findActive(userId);
        if (active != null) {
            log.info("已有进行中的会话，返回它 | userId={} | sessionId={}", userId, active.getId());
            return detail(userId, active.getId(), true);
        }

        // ---------- 3. 组装会话头 ----------
        WorkoutSession session = new WorkoutSession();
        session.setUserId(userId);
        session.setClientKey(request.clientKey());
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(request.startedAt() == null
                ? LocalDateTime.now() : request.startedAt());
        session.setIsDeload(0);

        // ---------- 4. 按计划训练：展开并快照 ----------
        List<ExpandedWorkout.ExerciseItem> items = List.of();

        if (request.programId() != null) {
            TodayWorkoutResponse today = todayWorkoutService.today(
                    userId, request.programId(), request.date(), request.dayNumber());

            if (today.dayNumber() == null) {
                // 计划还没开始 / 已经结束 / 计划里没有训练日。
                // 这里明确报错而不是建一个空会话——
                // 用户点的是「开始训练」，给他一个空训练比报错更让人困惑。
                throw new BizException(ErrorCode.BAD_REQUEST, String.format(
                        "当前没有可训练的内容（%s）", today.scheduleStateLabel()));
            }

            session.setProgramId(today.programId());
            session.setDayNumber(today.dayNumber());
            session.setDayName(today.dayName());
            session.setWeekNumber(today.weekNumber());
            session.setIsDeload(today.deloadWeek() ? 1 : 0);
            session.setWeightAdjustPct(today.weightAdjustPct());
            items = today.exercises();
        }
        // programId 为 null 时是临时训练（M4-A-3）：
        // 只建一个空会话，动作由客户端在跟练过程中逐个加。

        sessionMapper.insert(session);

        // ---------- 5. 深拷贝快照 ----------
        for (ExpandedWorkout.ExerciseItem item : items) {
            insertSnapshot(session.getId(), item);
        }

        log.info("创建训练会话 | userId={} | sessionId={} | programId={} | day={} | week={} | 动作数={}",
                userId, session.getId(), session.getProgramId(),
                session.getDayNumber(), session.getWeekNumber(), items.size());

        return detail(userId, session.getId(), false);
    }

    /**
     * 把一个展开后的动作**深拷贝**进快照。
     *
     * <p><b>这就是「不变量 1」的实现。</b>从这一刻起，
     * 计划怎么改都和这条会话无关了。
     *
     * <p>注意拷的是**展开后**的值：
     * <pre>
     *   计划里写          快照里存
     *   ─────────────    ──────────────
     *   第 5 周 +5%   →   set1 63kg  set2 63kg  set3 73.5kg
     *   （修饰）           （算好的具体值）
     * </pre>
     * 存修饰的话，用户几周后再看，还得拿当时的计划重新算一遍才能知道那天推多重。
     */
    private void insertSnapshot(Long sessionId, ExpandedWorkout.ExerciseItem item) {

        SessionExercise exercise = new SessionExercise();
        exercise.setSessionId(sessionId);
        exercise.setExerciseId(item.exerciseId());
        // 动作名是**显示用的真相**：动作后来被删除或改名，这条快照都不变
        exercise.setExerciseName(item.exerciseName());
        exercise.setPrimaryMuscle(item.primaryMuscle());
        exercise.setMetricType(item.metricType());
        exercise.setOrderIndex(item.orderIndex());
        exercise.setSupersetGroup(item.supersetGroup());
        exercise.setOrderInGroup(item.orderInGroup());
        exercise.setTargetSets(item.sets() == null ? 0 : item.sets().size());
        exercise.setNote(item.note());
        exercise.setStatus(SessionExerciseStatus.PENDING);
        sessionExerciseMapper.insert(exercise);

        if (item.sets() == null) {
            return;
        }

        for (ExpandedWorkout.SetItem set : item.sets()) {
            SessionSetTarget target = new SessionSetTarget();
            target.setSessionExerciseId(exercise.getId());
            target.setSetNumber(set.setNumber());
            target.setSetType(set.setType());
            target.setTargetReps(set.targetReps());
            target.setTargetRepsMin(set.targetRepsMin());
            target.setTargetRepsMax(set.targetRepsMax());

            // 目标强度是四元组，不是裸重量——RPE 处方算不出具体公斤数
            ExpandedWorkout.Target t = set.target();
            if (t != null) {
                target.setTargetWeightType(t.type());
                target.setTargetWeight(t.weight());
                target.setTargetWeightPct(t.pct());
                target.setTargetRpe(t.rpe());
            } else {
                // 纯自重动作没有目标强度（如引体向上只写次数）。
                // 类型仍要落库，否则读回来是 null，客户端得判空两次。
                target.setTargetWeightType(com.gymlog.program.TargetWeightType.ABSOLUTE);
            }

            // 展开时已解析过优先级链，这个值不会是 null
            target.setRestSec(set.restSec());
            target.setNote(set.note());
            sessionSetTargetMapper.insert(target);
        }
    }

    // ==================================================================
    // 状态流转
    // ==================================================================

    /**
     * 完成训练。
     *
     * <p><b>只有完成才推进训练日轮转。</b>见 {@link SessionStatus#COMPLETED}。
     *
     * @param durationSec 实际训练时长。由客户端上报，不由服务端算
     *                    （中途暂停了多久只有客户端知道）
     */
    @Transactional
    public SessionDetailResponse finish(Long userId, Long sessionId,
                                        Integer durationSec, String note) {
        WorkoutSession session = loadOwned(userId, sessionId);
        requireInProgress(session, "重复结束");

        WorkoutSession update = new WorkoutSession();
        update.setId(sessionId);
        update.setStatus(SessionStatus.COMPLETED);
        update.setFinishedAt(LocalDateTime.now());
        update.setDurationSec(durationSec);
        update.setNote(note);
        sessionMapper.updateById(update);

        log.info("完成训练 | userId={} | sessionId={} | 时长={}秒", userId, sessionId, durationSec);

        return detail(userId, sessionId, false);
    }

    /**
     * 放弃这次训练。
     *
     * <p><b>记录会保留</b>，只是不计入完成率和轮转。
     * 删除是另一回事（用户明确要抹掉），放弃是「我练了但没练完」。
     *
     * <p>和「练到一半被叫走」的区别在于用户是否还会回来继续。
     * 不点结束就一直是 IN_PROGRESS，下次打开还能续（断点续训）。
     */
    @Transactional
    public SessionDetailResponse abandon(Long userId, Long sessionId) {
        WorkoutSession session = loadOwned(userId, sessionId);
        requireInProgress(session, "放弃");

        WorkoutSession update = new WorkoutSession();
        update.setId(sessionId);
        update.setStatus(SessionStatus.ABANDONED);
        update.setFinishedAt(LocalDateTime.now());
        sessionMapper.updateById(update);

        log.info("放弃训练 | userId={} | sessionId={}", userId, sessionId);

        return detail(userId, sessionId, false);
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /**
     * 当前进行中的会话（断点续训用）。
     *
     * @return 没有进行中的会话时返回 null
     */
    public WorkoutSession findActive(Long userId) {
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<WorkoutSession>()
                        .eq(WorkoutSession::getUserId, userId)
                        .eq(WorkoutSession::getStatus, SessionStatus.IN_PROGRESS)
                        // 理论上只会有一条；按开始时间取最新的那条兜底，
                        // 万一因为历史数据或并发出现了两条，也不会抛 TooManyResults
                        .orderByDesc(WorkoutSession::getStartedAt)
                        .last("LIMIT 1"));
    }

    /** 会话详情（含完整快照）。{@code resumed} 由调用方指定 */
    public SessionDetailResponse detail(Long userId, Long sessionId, boolean resumed) {
        WorkoutSession session = loadOwned(userId, sessionId);

        List<SessionExercise> exercises = sessionExerciseMapper.selectList(
                new LambdaQueryWrapper<SessionExercise>()
                        .eq(SessionExercise::getSessionId, sessionId)
                        .orderByAsc(SessionExercise::getOrderIndex));

        // 一次查出所有逐组目标和实际记录，避免 N+1
        Map<Long, List<SessionSetTarget>> setsByExercise = loadSetTargets(exercises);
        Map<Long, List<SetRecord>> recordsByExercise = loadRecords(exercises);

        List<SessionDetailResponse.ExerciseItem> items = exercises.stream()
                .map(e -> SessionDetailResponse.exerciseFrom(e,
                        setsByExercise.getOrDefault(e.getId(), List.of())
                                .stream()
                                .map(SessionDetailResponse.SetTargetItem::from)
                                .toList(),
                        SetRecordResponse.Item.from(
                                recordsByExercise.getOrDefault(e.getId(), List.of()))))
                .toList();

        return SessionDetailResponse.assemble(session, items, resumed);
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    private WorkoutSession findByClientKey(Long userId, String clientKey) {
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<WorkoutSession>()
                        .eq(WorkoutSession::getUserId, userId)
                        .eq(WorkoutSession::getClientKey, clientKey));
    }

    /**
     * 加载属于该用户的会话。
     *
     * <p>不满足条件统一抛 404（而不是 403）——理由同计划：
     * 不泄露「这个 id 存在」。
     *
     * <p><b>public 是给 {@link SetRecordService} 用的</b>：
     * 组记录是会话内容的一部分，它需要同一套归属校验。
     * 各自实现一份的话，「查别人的会话返回 404」这条规则就有两个实现，
     * 迟早会有一个被改歪——而越权漏洞往往就是这么来的。
     */
    public WorkoutSession loadOwned(Long userId, Long sessionId) {
        WorkoutSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.isOwnedBy(userId)) {
            throw new BizException(ErrorCode.SESSION_NOT_FOUND);
        }
        return session;
    }

    /**
     * 要求会话处于进行中。
     *
     * <p>对已结束的会话再次点「完成」应该报错而不是静默成功——
     * 静默成功会让客户端以为第一次的请求丢了，
     * 从而重试出一个「已经完成但数据被覆盖」的状态。
     *
     * <p>同样对 {@link SetRecordService} 开放：已结束的训练不能再记录新组，
     * 否则历史数据会在用户点完「结束」之后继续变化，完成率就算不准了。
     */
    public void requireInProgress(WorkoutSession session, String action) {
        if (!session.isInProgress()) {
            // 文案用**实际状态**拼，而不是让调用方传一句状态描述。
            //
            // 原来的写法是 `"该训练" + action + "了，无法重复操作"`，
            // 调用方传的是「已完成」「已放弃」「已结束」这类描述。
            // 结果「记录一组」这条路径拼出了
            // 「该训练已结束了，无法重复操作」——不通，而且「重复操作」也不对，
            // 用户是第一次记录，不是重复。
            //
            // 改成「该训练<当前状态>，无法<动作>」之后，调用方只需传动词：
            //   该训练已完成，无法重复结束
            //   该训练已放弃，无法记录
            throw new BizException(ErrorCode.SESSION_ALREADY_COMPLETED, String.format(
                    "该训练%s，无法%s",
                    session.getStatus().getDisplayName(), action));
        }
    }

    /**
     * 一次查出所有动作的逐组目标。
     *
     * <p>空列表时直接返回空 Map——不查库。
     * 不判空的话会生成 {@code WHERE id IN ()} 这种无意义的 SQL。
     */
    /**
     * 一次查出所有动作的实际组记录。
     *
     * <p>和 {@link #loadSetTargets} 同一个模式：一个 IN 查询，
     * 不在循环里逐动作查。一个会话 5 个动作就是 5 次往返，
     * 而断点续训是打开 App 就会走的路径。
     */
    private Map<Long, List<SetRecord>> loadRecords(List<SessionExercise> exercises) {
        if (exercises.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = exercises.stream().map(SessionExercise::getId).toList();
        return setRecordMapper.selectList(
                        new LambdaQueryWrapper<SetRecord>()
                                .in(SetRecord::getSessionExerciseId, ids)
                                .orderByAsc(SetRecord::getSetNumber))
                .stream()
                .collect(Collectors.groupingBy(SetRecord::getSessionExerciseId));
    }

    private Map<Long, List<SessionSetTarget>> loadSetTargets(List<SessionExercise> exercises) {
        if (exercises.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = exercises.stream().map(SessionExercise::getId).toList();
        return sessionSetTargetMapper.selectList(
                        new LambdaQueryWrapper<SessionSetTarget>()
                                .in(SessionSetTarget::getSessionExerciseId, ids)
                                .orderByAsc(SessionSetTarget::getSetNumber))
                .stream()
                .collect(Collectors.groupingBy(SessionSetTarget::getSessionExerciseId));
    }

}
