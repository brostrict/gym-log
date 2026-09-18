package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.program.Program;
import com.gymlog.program.ProgramMapper;
import com.gymlog.program.ProgramService;
import com.gymlog.program.ProgramStatus;
import com.gymlog.program.TargetWeightType;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.training.dto.TodayWorkoutResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 「今天练什么」的编排逻辑。
 *
 * <p>周次与轮转的计算本身在 {@link TrainingScheduleTest} 里已经穷尽测过，
 * 这里只测**编排**：选哪个计划、怎么定训练日、展开结果对不对。
 */
@SpringBootTest
@Transactional
class TodayWorkoutServiceTest {

    private static final Long USER = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Autowired private TodayWorkoutService service;
    @Autowired private ProgramService programService;
    @Autowired private ProgramMapper programMapper;
    @Autowired private ExerciseMapper exerciseMapper;

    private Long benchId;
    private Long squatId;

    @BeforeEach
    void setUp() {
        benchId = findExercise("杠铃卧推").getId();
        squatId = findExercise("杠铃深蹲").getId();
    }

    // ==================================================================
    // 一、没有计划
    // ==================================================================

    @Test
    @DisplayName("没有进行中的计划时返回 200 且 programId 为 null，而不是 404")
    void noProgramIsNotAnError() {
        TodayWorkoutResponse r = service.today(USER, null, TODAY, null);

        // 「我没有计划」是正常状态。用 404 会让客户端把空状态
        // 和真正的请求失败混在一起处理。
        assertThat(r.programId()).isNull();
        assertThat(r.date()).isEqualTo(TODAY);
        assertThat(r.exercises()).isEmpty();
        assertThat(r.dayOptions()).isEmpty();
    }

    // ==================================================================
    // 二、选计划
    // ==================================================================

    @Test
    @DisplayName("多个进行中的计划时，选已开始的里面开始得最晚的")
    void picksMostRecentlyStarted() {
        createProgram("老计划", LocalDate.of(2026, 1, 1));
        Long newer = createProgram("新计划", LocalDate.of(2026, 9, 1));
        createProgram("还没开始的计划", LocalDate.of(2026, 12, 1));

        TodayWorkoutResponse r = service.today(USER, null, TODAY, null);

        // 最近开始的那个才是「当前在练的」。
        // 未来才开始的计划不该冒到首页上。
        assertThat(r.programId()).isEqualTo(newer);
        assertThat(r.programName()).isEqualTo("新计划");
    }

    @Test
    @DisplayName("都是未来计划时，取最近创建的那个，状态为未开始")
    void fallsBackToMostRecentlyCreated() {
        createProgram("先建的", LocalDate.of(2026, 12, 1));
        Long latest = createProgram("后建的", LocalDate.of(2026, 12, 10));

        TodayWorkoutResponse r = service.today(USER, null, TODAY, null);

        assertThat(r.programId()).isEqualTo(latest);
        assertThat(r.scheduleState()).isEqualTo(ScheduleState.NOT_STARTED);
        assertThat(r.daysUntilStart()).isPositive();
    }

    @Test
    @DisplayName("暂停的计划不会被自动选中，但可以显式指定")
    void pausedProgramIsNotAutoPicked() {
        Long paused = createProgram("暂停的计划", LocalDate.of(2026, 9, 1));
        Program update = new Program();
        update.setId(paused);
        update.setStatus(ProgramStatus.PAUSED);
        programMapper.updateById(update);

        // 自动挑选：没有 ACTIVE 的，返回空
        assertThat(service.today(USER, null, TODAY, null).programId()).isNull();

        // 显式指定：可以查，客户端据此显示「已暂停」角标
        TodayWorkoutResponse explicit = service.today(USER, paused, TODAY, null);
        assertThat(explicit.programId()).isEqualTo(paused);
        assertThat(explicit.programStatus()).isEqualTo("PAUSED");
    }

    @Test
    @DisplayName("归档的计划不能查")
    void archivedProgramIsRejected() {
        Long programId = createProgram("归档的计划", LocalDate.of(2026, 9, 1));
        Program update = new Program();
        update.setId(programId);
        update.setStatus(ProgramStatus.ARCHIVED);
        programMapper.updateById(update);

        assertThatThrownBy(() -> service.today(USER, programId, TODAY, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    @Test
    @DisplayName("别人的计划查不到，且不泄露是否存在")
    void otherUsersProgramIsNotFound() {
        Long programId = createProgram("我的计划", LocalDate.of(2026, 9, 1));

        assertThatThrownBy(() -> service.today(999L, programId, TODAY, null))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
    }

    // ==================================================================
    // 三、定训练日
    // ==================================================================

    @Test
    @DisplayName("默认按轮转推荐第 1 个训练日（还没练过）")
    void defaultsToFirstDayByRotation() {
        Long programId = createTwoDayProgram(LocalDate.of(2026, 9, 14));

        TodayWorkoutResponse r = service.today(USER, programId, TODAY, null);

        assertThat(r.dayNumber()).isEqualTo(1);
        assertThat(r.dayName()).isEqualTo("推日");
        // 这个测试里没有创建过训练会话，所以已完成次数是 0。
        // 轮转本身的推进逻辑在 SessionServiceTest 里端到端验证
        // （完成一场 → 推荐换到下一个训练日）。
        assertThat(r.completedSessions()).isZero();
    }

    @Test
    @DisplayName("显式指定训练日时覆盖轮转")
    void explicitDayOverridesRotation() {
        Long programId = createTwoDayProgram(LocalDate.of(2026, 9, 14));

        TodayWorkoutResponse r = service.today(USER, programId, TODAY, 2);

        // M4-A-4「切换到本周其他训练日」
        assertThat(r.dayNumber()).isEqualTo(2);
        assertThat(r.dayName()).isEqualTo("腿日");
    }

    @Test
    @DisplayName("指定不存在的训练日时明确报错，而不是静默给别的一天")
    void unknownDayIsRejected() {
        Long programId = createTwoDayProgram(LocalDate.of(2026, 9, 14));

        assertThatThrownBy(() -> service.today(USER, programId, TODAY, 99))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROGRAM_DAY_NOT_FOUND);
    }

    @Test
    @DisplayName("计划还没开始时不给训练日，但计划信息照常返回")
    void notStartedGivesNoDay() {
        Long programId = createTwoDayProgram(LocalDate.of(2026, 10, 1));

        TodayWorkoutResponse r = service.today(USER, programId, TODAY, null);

        assertThat(r.scheduleState()).isEqualTo(ScheduleState.NOT_STARTED);
        assertThat(r.dayNumber()).isNull();
        assertThat(r.exercises()).isEmpty();
        // 但切换列表仍然给出，客户端可以「预览」计划内容
        assertThat(r.dayOptions()).hasSize(2);
    }

    @Test
    @DisplayName("计划已结束时不给训练日")
    void finishedGivesNoDay() {
        // 2 周计划，开始于 8 月初，今天已经 9 月中
        Long programId = createTwoDayProgram(LocalDate.of(2026, 8, 3));

        TodayWorkoutResponse r = service.today(USER, programId, TODAY, null);

        assertThat(r.scheduleState()).isEqualTo(ScheduleState.FINISHED);
        assertThat(r.dayNumber()).isNull();
    }

    @Test
    @DisplayName("切换列表标出当前选中的那一天")
    void dayOptionsMarkCurrent() {
        Long programId = createTwoDayProgram(LocalDate.of(2026, 9, 14));

        TodayWorkoutResponse r = service.today(USER, programId, TODAY, 2);

        assertThat(r.dayOptions()).hasSize(2);
        assertThat(r.dayOptions())
                .filteredOn(TodayWorkoutResponse.DayOption::current)
                .singleElement()
                .satisfies(o -> {
                    assertThat(o.dayNumber()).isEqualTo(2);
                    assertThat(o.name()).isEqualTo("腿日");
                    assertThat(o.exerciseCount()).isEqualTo(1);
                });
    }

    // ==================================================================
    // 四、与展开逻辑一致
    // ==================================================================

    @Test
    @DisplayName("返回的动作已经应用了当周的强度调整")
    void appliesWeekAdjustment() {
        // 开始于 9/7，今天 9/18 → 第 11 天 → 第 2 周（+10%）
        Long programId = createTwoDayProgram(LocalDate.of(2026, 9, 7));

        TodayWorkoutResponse r = service.today(USER, programId, TODAY, 1);

        assertThat(r.weekNumber()).isEqualTo(2);
        assertThat(r.weightAdjustPct()).isEqualByComparingTo("10");

        // 60kg × 1.10 = 66kg。「今天练什么」显示的重量必须和
        // 跟练界面、计划预览里看到的完全一致——三处共用一份展开逻辑。
        assertThat(r.exercises()).hasSize(1);
        assertThat(r.exercises().get(0).sets())
                .allSatisfy(s -> assertThat(s.target().weight()).isEqualByComparingTo("66"));
    }

    @Test
    @DisplayName("减量周标记正确带出")
    void carriesDeloadFlag() {
        Long programId = createProgram(
                "带减量周的计划", LocalDate.of(2026, 9, 7),
                List.of(week(1, "0", false), week(2, "-40", true)),
                List.of(day(1, "推日", prescription(benchId, 1, 3, 8, 10, 120, "60"))));

        // 9/7 开始，9/18 是第 11 天 → 第 2 周（减量）
        TodayWorkoutResponse r = service.today(USER, programId, TODAY, 1);

        assertThat(r.weekNumber()).isEqualTo(2);
        assertThat(r.deloadWeek()).isTrue();
        // 60 × 0.6 = 36
        assertThat(r.exercises().get(0).sets().get(0).target().weight())
                .isEqualByComparingTo("36");
    }

    // ==================================================================
    // 测试数据
    // ==================================================================

    /** 建一个两天的计划：推日（卧推）+ 腿日（深蹲） */
    private Long createTwoDayProgram(LocalDate startDate) {
        return createProgram("两日计划", startDate,
                List.of(week(1, "0", false), week(2, "10", false)),
                List.of(
                        day(1, "推日", prescription(benchId, 1, 3, 8, 10, 120, "60")),
                        day(2, "腿日", prescription(squatId, 1, 5, 5, 5, 180, "100"))));
    }

    /** 建一个单日计划，默认从今天往前 4 天开始（落在第 1 周） */
    private Long createProgram(String name, LocalDate startDate) {
        return createProgram(name, startDate,
                List.of(week(1, "0", false)),
                List.of(day(1, "推日", prescription(benchId, 1, 3, 8, 10, 120, "60"))));
    }

    private Long createProgram(String name,
                               LocalDate startDate,
                               List<ProgramCreateRequest.WeekRequest> weeks,
                               List<ProgramCreateRequest.DayRequest> days) {
        return programService.create(USER, new ProgramCreateRequest(
                name, null, weeks.size(), startDate, weeks, days));
    }

    private static ProgramCreateRequest.WeekRequest week(int number, String adjust, boolean deload) {
        return new ProgramCreateRequest.WeekRequest(
                number, 3, new BigDecimal(adjust), 0, deload, null);
    }

    private static ProgramCreateRequest.DayRequest day(
            int number, String name, ProgramCreateRequest.PrescriptionRequest... exercises) {
        return new ProgramCreateRequest.DayRequest(number, name, false, null, List.of(exercises));
    }

    private static ProgramCreateRequest.PrescriptionRequest prescription(
            Long exerciseId, int order, int sets, int repsMin, int repsMax, int restSec, String weight) {
        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId, order, null, null, sets, repsMin, repsMax, restSec,
                TargetWeightType.ABSOLUTE, new BigDecimal(weight), null, null, null, null);
    }

    private Exercise findExercise(String name) {
        Exercise e = exerciseMapper.selectOne(
                new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID)
                        .eq(Exercise::getName, name));
        assertThat(e).as("动作库里应存在：" + name).isNotNull();
        return e;
    }
}
