package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.program.dto.ExpandedWorkout;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 周期化展开的数据加载层。
 *
 * <h3>职责边界</h3>
 *
 * <p>这个类**只做三件事**：查数据 → 组装参数 → 调用
 * {@link ProgramExpander}。所有计算都在展开函数里。
 *
 * <p>分两层的理由：展开函数要能被「预览今天练什么」和
 * 「创建训练会话快照」（Phase 3）两处复用，
 * 而这两处的数据来源和权限校验方式不同。
 * 把查询留在这一层，展开逻辑就只有一份。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpansionService {

    private final ProgramStructureSupport structureSupport;
    private final WeekTemplateMapper weekTemplateMapper;
    private final DayTemplateMapper dayTemplateMapper;
    private final PrescribedExerciseMapper prescribedExerciseMapper;

    /**
     * 展开「第 N 周第 M 天具体练什么」。
     *
     * @param weekNumber 第几周。<b>允许为 null</b>——表示不做周期化调整，
     *                   按计划里写的基准值展开。
     *                   不限期计划、或用户没配周期化时就是这个情况。
     * @param dayNumber  第几个训练日（计划内的序号，不是星期几）
     */
    public ExpandedWorkout expandDay(Long userId, Long programId,
                                     Integer weekNumber, Integer dayNumber) {

        // 归属校验。不通过统一 404，不泄露「这个 id 存在」
        Program program = structureSupport.loadOwned(userId, programId);

        WeekTemplate week = resolveWeek(programId, weekNumber);
        DayTemplate day = resolveDay(programId, dayNumber);

        // 休息日不能「跟练」。这不是错误数据，是用户选错了日子——
        // 所以给 400 而不是 404：资源是存在的，只是不能对它做这个操作。
        if (day.isRest()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "「" + day.getName() + "」是休息日，没有训练内容");
        }

        List<PrescribedExercise> prescriptions = prescribedExerciseMapper.selectList(
                new LambdaQueryWrapper<PrescribedExercise>()
                        .eq(PrescribedExercise::getDayTemplateId, day.getId()));

        // ⚠️ 必须排序后再传进展开函数。
        // 展开函数是纯函数，不查库也不排序——
        // 顺序由调用方保证，这样它的行为才完全由入参决定。
        List<PrescribedExercise> sorted = structureSupport.sortPrescriptions(prescriptions);

        Map<Long, List<PrescribedSet>> setsByExercise = structureSupport.loadSets(sorted);
        Map<Long, Exercise> exercisesById = structureSupport.loadExercises(sorted);

        if (log.isDebugEnabled()) {
            log.debug("展开训练内容 | userId={} | programId={} | week={} | day={} | 动作数={}",
                    userId, programId, weekNumber, dayNumber, sorted.size());
        }

        return ProgramExpander.expand(program, week, day, sorted, setsByExercise, exercisesById);
    }

    /**
     * 解析周修饰。
     *
     * <p><b>三种情况都返回 null（= 不做调整），而不是报错</b>：
     * <ol>
     *   <li>{@code weekNumber} 没传 —— 客户端只要基准值</li>
     *   <li>传了但计划里没有这一周 —— 计划可能压根没配周期化</li>
     *   <li>计划总共只有 1 周</li>
     * </ol>
     *
     * <p><b>为什么第 2 种情况不报错</b>：用户建一个「每周三练」的简单计划时，
     * 通常不会去填周表。如果严格校验，这种计划一旦带上 {@code ?week=1}
     * 就直接 400，而他其实什么都没做错。
     *
     * <p>代价是「周号打错」也不会被提示。所以这里**记一条 info 日志**，
     * 并在返回体里把 {@code weekNumber} 置空——
     * 客户端看到 null 就知道这次没有应用任何周期化调整。
     */
    private WeekTemplate resolveWeek(Long programId, Integer weekNumber) {
        if (weekNumber == null) {
            return null;
        }

        WeekTemplate week = weekTemplateMapper.selectOne(
                new LambdaQueryWrapper<WeekTemplate>()
                        .eq(WeekTemplate::getProgramId, programId)
                        .eq(WeekTemplate::getWeekNumber, weekNumber));

        if (week == null) {
            log.info("计划未配置该周，按基准值展开 | programId={} | weekNumber={}",
                    programId, weekNumber);
        }

        return week;
    }

    /**
     * 按 dayNumber 找训练日。
     *
     * <p>找不到就是真的找不到——用户点了一个不存在的训练日，
     * 必须明确告诉他，而不是返回一个空训练。
     */
    private DayTemplate resolveDay(Long programId, Integer dayNumber) {
        if (dayNumber == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "缺少训练日序号");
        }

        DayTemplate day = dayTemplateMapper.selectOne(
                new LambdaQueryWrapper<DayTemplate>()
                        .eq(DayTemplate::getProgramId, programId)
                        .eq(DayTemplate::getDayNumber, dayNumber));

        if (day == null) {
            throw new BizException(ErrorCode.PROGRAM_DAY_NOT_FOUND,
                    "该计划没有第 " + dayNumber + " 个训练日");
        }

        return day;
    }
}
