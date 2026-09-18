package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.program.DayTemplate;
import com.gymlog.program.DayTemplateMapper;
import com.gymlog.program.ExpansionService;
import com.gymlog.program.PrescribedExercise;
import com.gymlog.program.PrescribedExerciseMapper;
import com.gymlog.program.Program;
import com.gymlog.program.ProgramMapper;
import com.gymlog.program.ProgramStatus;
import com.gymlog.program.ProgramStructureSupport;
import com.gymlog.program.dto.ExpandedWorkout;
import com.gymlog.training.dto.TodayWorkoutResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 「今天练什么」。
 *
 * <h3>职责</h3>
 * <pre>
 *   1. 选计划        —— 哪个计划算「当前在练的」
 *   2. 算位置        —— 第几周、该练第几天   ← 委托给纯函数 TrainingSchedule
 *   3. 展开内容      —— 委托给 ExpansionService（与 2.13 共用一份实现）
 * </pre>
 *
 * <p>这个类**只做编排**，不做计算。三条逻辑各自有归属，
 * 复制或改写任何一条都会造成两端行为不一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodayWorkoutService {

    private final ProgramMapper programMapper;
    private final DayTemplateMapper dayTemplateMapper;
    private final PrescribedExerciseMapper prescribedExerciseMapper;
    private final ProgramStructureSupport structureSupport;
    private final ExpansionService expansionService;
    private final SessionCounter sessionCounter;

    /**
     * @param programId 指定计划。<b>为 null 时自动挑选</b>（见 {@link #pickActiveProgram}）
     * @param date      查询哪一天。为 null 时用今天
     * @param dayNumber 指定训练日。为 null 时按轮转推荐
     */
    public TodayWorkoutResponse today(Long userId, Long programId, LocalDate date, Integer dayNumber) {

        LocalDate targetDate = date == null ? LocalDate.now() : date;

        Program program = programId == null
                ? pickActiveProgram(userId, targetDate)
                : structureSupport.loadOwned(userId, programId);

        if (program == null) {
            // 没有计划是**正常状态**，不是错误。返回 200 + programId=null
            return TodayWorkoutResponse.noProgram(targetDate);
        }

        // 归档的计划不能练。自动挑选时已经排除了，这里挡的是显式指定的情况
        if (program.getStatus() == ProgramStatus.ARCHIVED) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND, "计划已归档");
        }

        // ---------- 训练日列表（不含休息日，按序号排） ----------
        List<DayTemplate> trainingDays = dayTemplateMapper.selectList(
                        new LambdaQueryWrapper<DayTemplate>()
                                .eq(DayTemplate::getProgramId, program.getId())
                                .orderByAsc(DayTemplate::getDayNumber))
                .stream()
                .filter(d -> !d.isRest())
                .toList();

        // ---------- 算位置 ----------
        int completed = sessionCounter.completedCount(userId, program.getId());

        TrainingSchedule.Resolution resolution = TrainingSchedule.resolve(
                program.getStartDate(),
                program.getTotalWeeks(),
                targetDate,
                trainingDays.size(),
                completed);

        // ---------- 定哪一天 ----------
        DayTemplate targetDay = resolveDay(program, trainingDays, resolution, dayNumber);

        // ---------- 展开内容 ----------
        //
        // 复用 ExpansionService，而不是在这里再写一遍「查处方 → 展开」。
        // 代价是多一次 program 查询（它内部也会 loadOwned），
        // 换来的是**展开逻辑只有一份**——跟练、预览、今天练什么
        // 三处看到的重量必须完全一致，不一致是查不出来的 bug。
        ExpandedWorkout expanded = targetDay == null
                ? null
                : expansionService.expandDay(userId, program.getId(),
                        resolution.weekNumber(), targetDay.getDayNumber());

        // ---------- 组装 ----------
        Map<Long, Integer> exerciseCounts = countExercisesByDay(
                trainingDays.stream().map(DayTemplate::getId).toList());

        List<TodayWorkoutResponse.DayOption> options = trainingDays.stream()
                .map(d -> new TodayWorkoutResponse.DayOption(
                        d.getDayNumber(),
                        d.getName(),
                        exerciseCounts.getOrDefault(d.getId(), 0),
                        targetDay != null && d.getId().equals(targetDay.getId())))
                .toList();

        if (log.isDebugEnabled()) {
            log.debug("今天练什么 | userId={} | programId={} | date={} | week={} | state={} | day={}",
                    userId, program.getId(), targetDate, resolution.weekNumber(),
                    resolution.state(), targetDay == null ? null : targetDay.getDayNumber());
        }

        return new TodayWorkoutResponse(
                targetDate,
                program.getId(),
                program.getName(),
                program.getStatus().name(),
                program.getStatus().getDisplayName(),
                resolution.weekNumber(),
                resolution.totalWeeks(),
                resolution.state(),
                resolution.state().getDisplayName(),
                resolution.daysUntilStart(),
                expanded != null && expanded.deloadWeek(),
                expanded == null ? null : expanded.weightAdjustPct(),
                targetDay == null ? null : targetDay.getDayNumber(),
                targetDay == null ? null : targetDay.getName(),
                completed,
                expanded == null ? List.of() : expanded.exercises(),
                options);
    }

    // ==================================================================
    // 选计划
    // ==================================================================

    /**
     * 挑出「当前在练的」计划。
     *
     * <p><b>规则（两条，按顺序）</b>：
     * <ol>
     *   <li>已经开始的（{@code startDate <= today}）里面，<b>开始得最晚</b>的那个——
     *       同时有好几个计划时，最近开始的那个才是当前在练的</li>
     *   <li>如果没有已开始的，取<b>最近创建</b>的那个——
     *       计划建好了但还没到开始日期，用户仍然希望首页显示它</li>
     * </ol>
     *
     * <p><b>为什么需要规则而不是「用户手动指定当前计划」</b>：
     * 手动指定要多一个字段、多一个切换入口、多一个「忘了切换」的坑。
     * 对个人应用，按日期推断足够准，而且**不会出现「没有任何计划被选中」的死状态**。
     *
     * <p>用户真想练另一个计划时，客户端传 {@code programId} 即可。
     *
     * <p>只考虑 {@code ACTIVE}：暂停的计划是用户主动停的，
     * 归档的更是明确不要了，都不该自动冒到首页上。
     */
    private Program pickActiveProgram(Long userId, LocalDate today) {
        List<Program> actives = programMapper.selectList(
                new LambdaQueryWrapper<Program>()
                        .eq(Program::getUserId, userId)
                        .eq(Program::getStatus, ProgramStatus.ACTIVE)
                        // id 倒序 = 最近创建在前，作为兜底顺序
                        .orderByDesc(Program::getId));

        if (actives.isEmpty()) {
            return null;
        }

        return actives.stream()
                .filter(p -> p.getStartDate() != null && !p.getStartDate().isAfter(today))
                .max(Comparator.comparing(Program::getStartDate))
                .orElse(actives.get(0));
    }

    // ==================================================================
    // 定哪一天
    // ==================================================================

    /**
     * 决定今天练哪个训练日。
     *
     * <p><b>显式指定优先于轮转</b>——M4-A-4 要求支持「切换到本周其他训练日」。
     * 用户主动选了某一天，就不该被轮转覆盖。
     *
     * <p>三种情况返回 null（今天没有可练的内容）：
     * <ul>
     *   <li>计划里根本没有训练日</li>
     *   <li>计划还没开始（轮转没有意义）</li>
     *   <li>计划已经结束</li>
     * </ul>
     */
    private DayTemplate resolveDay(Program program,
                                   List<DayTemplate> trainingDays,
                                   TrainingSchedule.Resolution resolution,
                                   Integer dayNumber) {

        if (trainingDays.isEmpty()) {
            return null;
        }

        // ---------- 1. 显式指定 ----------
        if (dayNumber != null) {
            return trainingDays.stream()
                    .filter(d -> d.getDayNumber().equals(dayNumber))
                    .findFirst()
                    // 这里明确报错而不是回落到轮转：
                    // 用户点了一个不存在的训练日，静默给他别的一天会更困惑
                    .orElseThrow(() -> new BizException(ErrorCode.PROGRAM_DAY_NOT_FOUND,
                            "该计划没有第 " + dayNumber + " 个训练日"));
        }

        // ---------- 2. 还没开始 / 已结束：不推荐 ----------
        if (!resolution.isOngoing()) {
            return null;
        }

        // ---------- 3. 轮转 ----------
        Integer index = resolution.nextDayIndex();
        if (index == null || index < 0 || index >= trainingDays.size()) {
            return null;
        }
        return trainingDays.get(index);
    }

    /**
     * 一次查出所有训练日的动作数量。
     *
     * <p><b>一个 IN 查询，不是每天查一次</b>。一个 5 天的计划逐天查就是 5 次往返，
     * 而这个数据只是为了在切换列表里显示「推日 · 4 个动作」——
     * 为了一行装饰性文字付 5 次网络往返不划算。
     *
     * <p>{@code select(...)} 只取 {@code day_template_id} 一列：
     * 我们要的只是「每个训练日有几条」，把 40 条完整记录（含重量、次数、备注）
     * 全拉回来纯属浪费。
     */
    private Map<Long, Integer> countExercisesByDay(List<Long> dayIds) {
        if (dayIds.isEmpty()) {
            return Map.of();
        }
        return prescribedExerciseMapper.selectList(
                        new LambdaQueryWrapper<PrescribedExercise>()
                                .select(PrescribedExercise::getDayTemplateId)
                                .in(PrescribedExercise::getDayTemplateId, dayIds))
                .stream()
                .collect(Collectors.groupingBy(
                        PrescribedExercise::getDayTemplateId,
                        Collectors.summingInt(p -> 1)));
    }
}
