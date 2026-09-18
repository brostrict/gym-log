package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.exercise.MetricType;
import com.gymlog.exercise.MuscleGroup;
import com.gymlog.program.PrescribedExercise;
import com.gymlog.program.PrescribedExerciseMapper;
import com.gymlog.program.Program;
import com.gymlog.program.ProgramMapper;
import com.gymlog.program.ProgramService;
import com.gymlog.program.TargetWeightType;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.program.dto.ProgramStructureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话三张表的读写，以及**快照不变量**。
 *
 * <p>这个类里最重要的不是「表建对了」，而是
 * {@link #snapshotSurvivesProgramStructureEdit()} ——
 * 它验证的是整个项目最关键的一条设计承诺。
 */
@SpringBootTest
@Transactional
class WorkoutSessionMapperTest {

    private static final Long USER = 1L;

    @Autowired private WorkoutSessionMapper sessionMapper;
    @Autowired private SessionExerciseMapper sessionExerciseMapper;
    @Autowired private SessionSetTargetMapper sessionSetTargetMapper;
    @Autowired private ProgramService programService;
    @Autowired private ProgramMapper programMapper;
    @Autowired private PrescribedExerciseMapper prescribedExerciseMapper;
    @Autowired private ExerciseMapper exerciseMapper;

    private Long benchId;

    @BeforeEach
    void setUp() {
        benchId = findExercise("杠铃卧推").getId();
    }

    // ==================================================================
    // 一、读写往返
    // ==================================================================

    @Test
    @DisplayName("会话三层结构能正确读写，枚举往返无损")
    void roundTripsFullSessionTree() {
        Long sessionId = newSession("推日", "uuid-roundtrip", 60);

        WorkoutSession session = sessionMapper.selectById(sessionId);
        assertThat(session.getStatus()).isEqualTo(SessionStatus.IN_PROGRESS);
        assertThat(session.getDayName()).isEqualTo("推日");
        assertThat(session.getWeekNumber()).isEqualTo(2);
        assertThat(session.isDeload()).isFalse();
        assertThat(session.getWeightAdjustPct()).isEqualByComparingTo("5.00");
        assertThat(session.getClientKey()).isEqualTo("uuid-roundtrip");

        List<SessionExercise> exercises = sessionExerciseMapper.selectList(
                new LambdaQueryWrapper<SessionExercise>()
                        .eq(SessionExercise::getSessionId, sessionId));

        assertThat(exercises).hasSize(1);
        SessionExercise ex = exercises.get(0);
        assertThat(ex.getExerciseName()).isEqualTo("杠铃卧推");
        assertThat(ex.getPrimaryMuscle()).isEqualTo(MuscleGroup.CHEST);
        assertThat(ex.getMetricType()).isEqualTo(MetricType.WEIGHT_REPS);
        assertThat(ex.getStatus()).isEqualTo(SessionExerciseStatus.PENDING);
        assertThat(ex.getTargetSets()).isEqualTo(3);

        List<SessionSetTarget> sets = sessionSetTargetMapper.selectList(
                new LambdaQueryWrapper<SessionSetTarget>()
                        .eq(SessionSetTarget::getSessionExerciseId, ex.getId())
                        .orderByAsc(SessionSetTarget::getSetNumber));

        assertThat(sets).hasSize(3);
        assertThat(sets.get(0).getSetNumber()).isEqualTo(1);
        assertThat(sets.get(0).getSetType()).isEqualTo(SetType.WORKING);
        assertThat(sets.get(0).getTargetWeightType()).isEqualTo(TargetWeightType.ABSOLUTE);
        assertThat(sets.get(0).getTargetWeight()).isEqualByComparingTo("60.00");
        assertThat(sets.get(0).getTargetRepsMin()).isEqualTo(8);
        assertThat(sets.get(0).getTargetRepsMax()).isEqualTo(10);
        assertThat(sets.get(0).getRestSec()).isEqualTo(150);
    }

    // ==================================================================
    // 二、★ 快照不变量
    // ==================================================================

    @Test
    @DisplayName("★ 全量替换计划结构后，已建会话的快照完全不变")
    void snapshotSurvivesProgramStructureEdit() {
        // ---------- 1. 建计划（卧推 3 组 × 8-10 次 @ 60kg）----------
        Long programId = programService.create(USER, new ProgramCreateRequest(
                "快照验证计划", null, 8, LocalDate.of(2026, 9, 14),
                List.of(week(1, "0"), week(2, "5")),
                List.of(day(1, "推日", prescription(benchId, 3, 8, 10, 150, "60")))));

        // ---------- 2. 展开并创建会话快照 ----------
        Long sessionId = newSession("推日", "uuid-invariant", 60);
        Long sessionExerciseId = sessionExerciseMapper.selectList(
                        new LambdaQueryWrapper<SessionExercise>()
                                .eq(SessionExercise::getSessionId, sessionId))
                .get(0).getId();

        // 快照里的目标：60kg × 8-10 次
        SessionSetTarget before = firstSet(sessionExerciseId);
        assertThat(before.getTargetWeight()).isEqualByComparingTo("60.00");
        assertThat(before.getTargetRepsMin()).isEqualTo(8);

        // ---------- 3. 用户大改计划结构 ----------
        //
        // 注意这里是**全量替换**：旧的动作记录会被物理删除，
        // 新的记录带着全新的 id 插入。这正是最危险的场景。
        programService.updateStructure(USER, programId, new ProgramStructureRequest(
                List.of(week(1, "0"), week(2, "5")),
                List.of(day(1, "腿部日", prescription(benchId, 5, 3, 3, 240, "120"))),
                1));

        // 确认计划**确实**变了——否则下面那条断言可能只是因为改计划失败才通过
        Program program = programMapper.selectById(programId);
        assertThat(program.getVersion()).isEqualTo(2);

        List<PrescribedExercise> nowInProgram = prescribedExerciseMapper.selectList(
                new LambdaQueryWrapper<PrescribedExercise>()
                        .eq(PrescribedExercise::getDayTemplateId,
                                programService.detail(USER, programId).days().get(0).id()));

        assertThat(nowInProgram).hasSize(1);
        assertThat(nowInProgram.get(0).getTargetWeight()).isEqualByComparingTo("120.00");
        assertThat(nowInProgram.get(0).getTargetSets()).isEqualTo(5);

        // ---------- 4. ★ 快照必须原封不动 ----------
        SessionSetTarget after = firstSet(sessionExerciseId);

        assertThat(after.getTargetWeight())
                .as("计划改成 120kg 后，历史会话仍然显示当时的目标 60kg")
                .isEqualByComparingTo("60.00");
        assertThat(after.getTargetRepsMin())
                .as("次数也不该被改成 3")
                .isEqualTo(8);
        assertThat(after.getTargetRepsMax()).isEqualTo(10);
        assertThat(after.getRestSec())
                .as("休息时间也不该被改成 240 秒")
                .isEqualTo(150);

        WorkoutSession session = sessionMapper.selectById(sessionId);
        assertThat(session.getDayName())
                .as("训练日改名了，但历史记录显示的是当时那个名字")
                .isEqualTo("推日");
        assertThat(sessionExerciseMapper.selectById(sessionExerciseId).getTargetSets())
                .as("计划组数从 3 改成 5，快照仍是 3")
                .isEqualTo(3);
    }

    // ==================================================================
    // 三、幂等键
    // ==================================================================

    @Test
    @DisplayName("同一用户的同一 clientKey 不能重复插入（AC-5-1 的基础）")
    void rejectsDuplicateClientKey() {
        newSession("推日", "uuid-dup", 60);

        assertThatThrownBy(() -> newSession("推日", "uuid-dup", 60))
                .isInstanceOf(DuplicateKeyException.class);

        // 换个 key 就没事
        newSession("推日", "uuid-other", 60);
    }

    @Test
    @DisplayName("clientKey 为 NULL 时允许多条（手动补录不需要幂等）")
    void allowsMultipleNullClientKeys() {
        newSession("推日", null, 60);
        newSession("推日", null, 60);

        Long count = sessionMapper.selectCount(
                new LambdaQueryWrapper<WorkoutSession>().isNull(WorkoutSession::getClientKey));
        assertThat(count)
                .as("SQL 里 NULL ≠ NULL，所以唯一索引不会挡住它们")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("不同用户可以用同一个 clientKey")
    void clientKeyIsScopedToUser() {
        newSession("推日", "uuid-shared", 60);

        // 换一个 user_id，同样的 key 应该能插进去
        WorkoutSession other = new WorkoutSession();
        other.setUserId(2L);
        other.setClientKey("uuid-shared");
        other.setStatus(SessionStatus.IN_PROGRESS);
        other.setStartedAt(LocalDateTime.now());
        other.setIsDeload(0);
        assertThat(sessionMapper.insert(other)).isEqualTo(1);
    }

    @Test
    @DisplayName("临时训练的 programId 为 NULL")
    void adHocSessionHasNoProgram() {
        Long sessionId = newSession(null, "uuid-adhoc", 60);

        WorkoutSession session = sessionMapper.selectById(sessionId);
        assertThat(session.isAdHoc()).isTrue();
        assertThat(session.getProgramId()).isNull();
        assertThat(session.getDayNumber()).isNull();
    }

    // ==================================================================
    // 测试脚手架
    // ==================================================================

    /**
     * 手工建一条完整的会话快照。
     *
     * <p>这里**刻意不走 SessionService**（它还没实现）——
     * 表结构本身就该能独立验证，否则一旦创建逻辑有 bug，
     * 表的问题和逻辑的问题会混在一起。
     */
    private Long newSession(String dayName, String clientKey, int weight) {
        WorkoutSession session = new WorkoutSession();
        session.setUserId(USER);
        session.setClientKey(clientKey);
        session.setDayNumber(dayName == null ? null : 1);
        session.setDayName(dayName);
        session.setWeekNumber(2);
        session.setIsDeload(0);
        session.setWeightAdjustPct(new BigDecimal("5.00"));
        session.setStatus(SessionStatus.IN_PROGRESS);
        session.setStartedAt(LocalDateTime.of(2026, 9, 21, 19, 30));
        sessionMapper.insert(session);

        SessionExercise ex = new SessionExercise();
        ex.setSessionId(session.getId());
        ex.setExerciseId(benchId);
        ex.setExerciseName("杠铃卧推");
        ex.setPrimaryMuscle(MuscleGroup.CHEST);
        ex.setMetricType(MetricType.WEIGHT_REPS);
        ex.setOrderIndex(1);
        ex.setTargetSets(3);
        ex.setStatus(SessionExerciseStatus.PENDING);
        sessionExerciseMapper.insert(ex);

        for (int i = 1; i <= 3; i++) {
            SessionSetTarget t = new SessionSetTarget();
            t.setSessionExerciseId(ex.getId());
            t.setSetNumber(i);
            t.setSetType(SetType.WORKING);
            t.setTargetRepsMin(8);
            t.setTargetRepsMax(10);
            t.setTargetWeightType(TargetWeightType.ABSOLUTE);
            t.setTargetWeight(new BigDecimal(weight));
            t.setRestSec(150);
            sessionSetTargetMapper.insert(t);
        }

        return session.getId();
    }

    private SessionSetTarget firstSet(Long sessionExerciseId) {
        return sessionSetTargetMapper.selectOne(
                new LambdaQueryWrapper<SessionSetTarget>()
                        .eq(SessionSetTarget::getSessionExerciseId, sessionExerciseId)
                        .eq(SessionSetTarget::getSetNumber, 1));
    }

    private static ProgramCreateRequest.WeekRequest week(int number, String adjust) {
        return new ProgramCreateRequest.WeekRequest(
                number, 3, new BigDecimal(adjust), 0, false, null);
    }

    private static ProgramCreateRequest.DayRequest day(
            int number, String name, ProgramCreateRequest.PrescriptionRequest... exercises) {
        return new ProgramCreateRequest.DayRequest(number, name, false, null, List.of(exercises));
    }

    private static ProgramCreateRequest.PrescriptionRequest prescription(
            Long exerciseId, int sets, int repsMin, int repsMax, int restSec, String weight) {
        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId, 1, null, null, sets, repsMin, repsMax, restSec,
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
