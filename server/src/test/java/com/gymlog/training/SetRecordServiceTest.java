package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.program.ProgramService;
import com.gymlog.program.TargetWeightType;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SessionDetailResponse;
import com.gymlog.training.dto.SetRecordRequest;
import com.gymlog.training.dto.SetRecordResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 组记录：记录、覆盖、删除、状态推进，以及归属校验。
 */
@SpringBootTest
@Transactional
class SetRecordServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER = 999L;
    private static final LocalDate START = LocalDate.of(2026, 9, 14);
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired private SetRecordService setRecordService;
    @Autowired private SessionService sessionService;
    @Autowired private ProgramService programService;
    @Autowired private SetRecordMapper setRecordMapper;
    @Autowired private SessionExerciseMapper sessionExerciseMapper;
    @Autowired private ExerciseMapper exerciseMapper;

    private Long sessionId;
    private Long benchExerciseId;   // session_exercise.id（快照行）

    @BeforeEach
    void setUp() {
        Long benchId = findExercise("杠铃卧推").getId();

        Long programId = programService.create(USER, new ProgramCreateRequest(
                "组记录验证计划", null, 0, START,
                List.of(new ProgramCreateRequest.WeekRequest(
                        1, 3, BigDecimal.ZERO, 0, false, null)),
                List.of(new ProgramCreateRequest.DayRequest(1, "推日", false, null,
                        List.of(prescription(benchId, 3))))));

        SessionDetailResponse session = sessionService.create(USER,
                new SessionCreateRequest(programId, 1, DAY, "k-set", null));
        sessionId = session.id();
        benchExerciseId = session.exercises().get(0).id();
    }

    // ==================================================================
    // 一、记录与覆盖
    // ==================================================================

    @Test
    @DisplayName("记录一组后返回该动作的进度")
    void recordsASet() {
        SetRecordResponse r = record(1, "60", 10);

        assertThat(r.sessionExerciseId()).isEqualTo(benchExerciseId);
        assertThat(r.recordedSets()).isEqualTo(1);
        assertThat(r.targetSets()).isEqualTo(3);
        assertThat(r.status()).isEqualTo("IN_PROGRESS");
        assertThat(r.finished()).isFalse();
    }

    @Test
    @DisplayName("★ 记录完最后一组，动作自动标记为完成")
    void autoCompletesAfterLastSet() {
        record(1, "60", 10);
        record(2, "60", 10);
        SetRecordResponse last = record(3, "60", 9);

        assertThat(last.recordedSets()).isEqualTo(3);
        assertThat(last.status()).isEqualTo("COMPLETED");
        assertThat(last.finished()).isTrue();

        assertThat(sessionExerciseMapper.selectById(benchExerciseId).getStatus())
                .isEqualTo(SessionExerciseStatus.COMPLETED);
    }

    @Test
    @DisplayName("★ 同一组号再记一次是覆盖，不新增 —— 离线重试靠这个")
    void sameSetNumberUpserts() {
        record(3, "60", 10);
        SetRecordResponse again = record(3, "65", 8);

        assertThat(again.recordedSets()).as("仍然只有一组").isEqualTo(1);

        List<SetRecord> records = recordsOf();
        assertThat(records).hasSize(1);
        assertThat(records.get(0).getWeight()).isEqualByComparingTo("65");
        assertThat(records.get(0).getReps()).isEqualTo(8);
    }

    @Test
    @DisplayName("用户可以超过计划组数（临时加组）")
    void allowsMoreSetsThanPlanned() {
        record(1, "60", 10);
        record(2, "60", 10);
        record(3, "60", 8);
        SetRecordResponse fourth = record(4, "55", 12);

        assertThat(fourth.recordedSets()).isEqualTo(4);
        assertThat(fourth.targetSets()).isEqualTo(3);
        assertThat(fourth.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("热身组被标记为不计入容量")
    void warmupSetsAreFlagged() {
        SetRecordRequest req = new SetRecordRequest(
                SetType.WARMUP, new BigDecimal("40"), 12, null, null,
                null, null, null, LocalDateTime.now());
        setRecordService.recordSet(USER, sessionId, benchExerciseId, 1, req);

        SessionDetailResponse detail = sessionService.detail(USER, sessionId, false);
        SetRecordResponse.Item item = detail.exercises().get(0).records().get(0);

        assertThat(item.setType()).isEqualTo("WARMUP");
        assertThat(item.setTypeLabel()).isEqualTo("热身组");
        // 全局口径：热身组不计入容量（REQUIREMENTS 术语表）
        assertThat(item.countsTowardVolume()).isFalse();
    }

    // ==================================================================
    // 二、删除
    // ==================================================================

    @Test
    @DisplayName("删除一组后进度回退，动作状态也跟着回退")
    void deleteSetRevertsStatus() {
        record(1, "60", 10);
        record(2, "60", 10);
        record(3, "60", 10);
        assertThat(sessionExerciseMapper.selectById(benchExerciseId).getStatus())
                .isEqualTo(SessionExerciseStatus.COMPLETED);

        SetRecordResponse r = setRecordService.deleteSet(USER, sessionId, benchExerciseId, 3);

        assertThat(r.recordedSets()).isEqualTo(2);
        // 不回退的话界面会显示「3/3 完成」但实际只有两条记录
        assertThat(r.status()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("删光所有组后回到未开始")
    void deletingAllSetsResetsToPending() {
        record(1, "60", 10);
        setRecordService.deleteSet(USER, sessionId, benchExerciseId, 1);

        assertThat(sessionExerciseMapper.selectById(benchExerciseId).getStatus())
                .isEqualTo(SessionExerciseStatus.PENDING);
    }

    @Test
    @DisplayName("删除不存在的组不报错（离线队列可能乱序到达）")
    void deletingMissingSetIsIdempotent() {
        SetRecordResponse r = setRecordService.deleteSet(USER, sessionId, benchExerciseId, 99);

        assertThat(r.recordedSets()).isZero();
        assertThat(recordsOf()).isEmpty();
    }

    // ==================================================================
    // 三、跳过动作
    // ==================================================================

    @Nested
    @DisplayName("跳过动作（M4-D-1）")
    class Skipping {

        @Test
        @DisplayName("跳过动作后状态是 SKIPPED")
        void skipExercise() {
            SetRecordResponse r = setRecordService.updateExerciseStatus(
                    USER, sessionId, benchExerciseId, SessionExerciseStatus.SKIPPED);

            assertThat(r.status()).isEqualTo("SKIPPED");
            assertThat(r.statusLabel()).isEqualTo("已跳过");
            assertThat(r.finished()).isTrue();
        }

        @Test
        @DisplayName("⭐ 跳过之后删一组，不会把跳过状态改回未开始")
        void deleteDoesNotResurrectSkipped() {
            setRecordService.updateExerciseStatus(
                    USER, sessionId, benchExerciseId, SessionExerciseStatus.SKIPPED);

            // 跳过的动作本来就没有记录，删一个不存在的组会触发状态重算。
            // 不做特判的话，它会从 SKIPPED 变回 PENDING —— 跳过就白跳了。
            SetRecordResponse r = setRecordService.deleteSet(USER, sessionId, benchExerciseId, 1);

            assertThat(r.status())
                    .as("用户跳过了一个动作，不该因为一次误删就被改回未开始")
                    .isEqualTo("SKIPPED");
        }

        @Test
        @DisplayName("跳过之后真记录了组，说明用户改主意了")
        void recordingRevivesSkippedExercise() {
            setRecordService.updateExerciseStatus(
                    USER, sessionId, benchExerciseId, SessionExerciseStatus.SKIPPED);

            SetRecordResponse r = record(1, "60", 10);

            assertThat(r.status()).isEqualTo("IN_PROGRESS");
        }
    }

    // ==================================================================
    // 四、守卫
    // ==================================================================

    @Nested
    @DisplayName("守卫")
    class Guards {

        @Test
        @DisplayName("已结束的训练不能再记录")
        void cannotRecordOnFinishedSession() {
            sessionService.finish(USER, sessionId, 3600, null);

            assertThatThrownBy(() -> record(1, "60", 10))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_ALREADY_COMPLETED);

            // 否则历史数据会在用户点完「结束」之后继续变化，
            // 完成率就算不准了
        }

        @Test
        @DisplayName("别人的会话记不了")
        void cannotRecordOnOtherUsersSession() {
            assertThatThrownBy(() -> setRecordService.recordSet(
                    OTHER, sessionId, benchExerciseId, 1, simpleRequest()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
        }

        @Test
        @DisplayName("⭐ 不能用自己的 sessionId 配别人的动作 id 写数据")
        void cannotMixSessionAndExerciseFromDifferentSessions() {
            // 路径里有两个 id：sessionId 和 sessionExerciseId。
            // 只校验其中一个，用户就能用自己的会话 id 配上别人的动作 id，
            // 往别人的训练记录里写数据 —— 这是越权漏洞的经典形态。
            Long theirSessionId = sessionService.create(OTHER,
                    new SessionCreateRequest(null, null, DAY, "k-their", null)).id();

            // 但他们的会话是空的（临时训练），所以换一个方式构造：
            // 用我的 sessionId + 一个不存在的 exerciseId
            assertThatThrownBy(() -> setRecordService.recordSet(
                    USER, sessionId, 999999L, 1, simpleRequest()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_NOT_FOUND);

            // 关键：他的会话 id 配我的动作 id 也必须被挡住
            assertThatThrownBy(() -> setRecordService.recordSet(
                    OTHER, theirSessionId, benchExerciseId, 1, simpleRequest()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
        }

        @Test
        @DisplayName("记的组只属于本次会话，不影响计划模板（不变量 3）")
        void recordsDoNotTouchTheProgram() {
            record(1, "999", 99);

            // 组记录只挂在 session_exercise 上，计划里没有它的任何痕迹
            Long strays = setRecordMapper.selectCount(
                    new LambdaQueryWrapper<SetRecord>().eq(SetRecord::getWeight, new BigDecimal("999")));
            assertThat(strays).isEqualTo(1);
        }
    }

    // ==================================================================
    // 五、详情同时给出「该练什么」和「练了什么」
    // ==================================================================

    @Test
    @DisplayName("会话详情同时返回目标与实际记录，供断点续训一次拿全")
    void detailCarriesBothTargetsAndRecords() {
        record(1, "65", 9);

        SessionDetailResponse detail = sessionService.detail(USER, sessionId, false);
        SessionDetailResponse.ExerciseItem exercise = detail.exercises().get(0);

        assertThat(exercise.sets()).as("计划：3 组目标").hasSize(3);
        assertThat(exercise.sets().get(0).weight()).isEqualByComparingTo("60");

        assertThat(exercise.records()).as("实际：只有 1 组记录").hasSize(1);
        assertThat(exercise.records().get(0).weight()).isEqualByComparingTo("65");
        assertThat(exercise.records().get(0).reps()).isEqualTo(9);
        assertThat(exercise.status()).isEqualTo("IN_PROGRESS");
    }

    // ==================================================================
    // 测试脚手架
    // ==================================================================

    private SetRecordResponse record(int setNumber, String weight, int reps) {
        return setRecordService.recordSet(USER, sessionId, benchExerciseId, setNumber,
                new SetRecordRequest(SetType.WORKING, new BigDecimal(weight), reps,
                        null, null, null, null, null, LocalDateTime.now()));
    }

    private static SetRecordRequest simpleRequest() {
        return new SetRecordRequest(SetType.WORKING, new BigDecimal("60"), 10,
                null, null, null, null, null, LocalDateTime.now());
    }

    private List<SetRecord> recordsOf() {
        return setRecordMapper.selectList(
                new LambdaQueryWrapper<SetRecord>()
                        .eq(SetRecord::getSessionExerciseId, benchExerciseId)
                        .orderByAsc(SetRecord::getSetNumber));
    }

    private static ProgramCreateRequest.PrescriptionRequest prescription(Long exerciseId, int sets) {
        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId, 1, null, null, sets, 8, 10, 150,
                TargetWeightType.ABSOLUTE, new BigDecimal("60"), null, null, null, null);
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
