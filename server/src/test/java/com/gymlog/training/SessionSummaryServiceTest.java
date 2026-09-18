package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.program.ProgramService;
import com.gymlog.program.TargetWeightType;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SessionDetailResponse;
import com.gymlog.training.dto.SessionListItem;
import com.gymlog.training.dto.SessionSummaryResponse;
import com.gymlog.training.dto.SetRecordRequest;
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

/**
 * 训练总结与历史列表。
 *
 * <p>包含一个**契约测试**：守 SQL 聚合里的 e1RM 公式和 Java 里的一致。
 * 那两个实现分开是性能考虑，但公式必须只有一个「意思」。
 */
@SpringBootTest
@Transactional
class SessionSummaryServiceTest {

    private static final Long USER = 1L;
    private static final LocalDate START = LocalDate.of(2026, 9, 14);

    @Autowired private SessionSummaryService summaryService;
    @Autowired private SessionService sessionService;
    @Autowired private SetRecordService setRecordService;
    @Autowired private ProgramService programService;
    @Autowired private SetRecordMapper setRecordMapper;
    @Autowired private SessionExerciseMapper sessionExerciseMapper;
    @Autowired private ExerciseMapper exerciseMapper;

    private Long programId;
    private Long benchId;

    @BeforeEach
    void setUp() {
        benchId = findExercise("杠铃卧推").getId();
        programId = programService.create(USER, new ProgramCreateRequest(
                "总结验证计划", null, 0, START,
                List.of(new ProgramCreateRequest.WeekRequest(
                        1, 3, BigDecimal.ZERO, 0, false, null)),
                List.of(new ProgramCreateRequest.DayRequest(1, "推日", false, null,
                        List.of(new ProgramCreateRequest.PrescriptionRequest(
                                benchId, 1, null, null, 3, 8, 10, 150,
                                TargetWeightType.ABSOLUTE, new BigDecimal("60"),
                                null, null, null, null))))));
    }

    // ==================================================================
    // 一、统计
    // ==================================================================

    @Test
    @DisplayName("⭐ 容量只算正式组，热身组排除在外（AC-7-1）")
    void volumeExcludesWarmup() {
        Long sessionId = startSession("s-vol", LocalDate.of(2026, 9, 21));
        Long exId = firstExerciseId(sessionId);

        // 热身 40kg × 12 = 480（不该计入）
        record(sessionId, exId, 1, SetType.WARMUP, "40", 12);
        // 正式 60 × 10 = 600，60 × 8 = 480 → 1080
        record(sessionId, exId, 2, SetType.WORKING, "60", 10);
        record(sessionId, exId, 3, SetType.WORKING, "60", 8);

        SessionSummaryResponse s = summaryService.summary(USER, sessionId);

        assertThat(s.volume()).as("480 的热身组不能混进来").isEqualByComparingTo("1080");
        assertThat(s.workingSets()).isEqualTo(2);
        assertThat(s.warmupSets()).isEqualTo(1);
        assertThat(s.totalReps()).isEqualTo(18);
    }

    @Test
    @DisplayName("⭐ 容量与组数都给 —— 它们回答不同的问题（AC-7-7）")
    void bothVolumeAndSetsAreReported() {
        Long sessionId = startSession("s-both", LocalDate.of(2026, 9, 21));
        Long exId = firstExerciseId(sessionId);

        record(sessionId, exId, 1, SetType.WORKING, "100", 5);

        SessionSummaryResponse s = summaryService.summary(USER, sessionId);

        assertThat(s.volume()).isEqualByComparingTo("500");
        assertThat(s.workingSets()).isEqualTo(1);
        // 一个是 kg 一个是组，数值不同、单位不同，都对
        assertThat(s.volume()).isNotEqualByComparingTo(BigDecimal.valueOf(s.workingSets()));
    }

    // ==================================================================
    // 二、PR
    // ==================================================================

    @Nested
    @DisplayName("个人纪录")
    class PersonalRecords {

        @Test
        @DisplayName("第一次做某个动作就算 PR（用户确实创造了第一个纪录）")
        void firstTimeIsARecord() {
            Long sessionId = startSession("pr-1", LocalDate.of(2026, 9, 21));
            record(sessionId, firstExerciseId(sessionId), 1, SetType.WORKING, "60", 8);

            List<SessionSummaryResponse.PersonalRecordItem> prs =
                    summaryService.summary(USER, sessionId).personalRecords();

            assertThat(prs).hasSize(1);
            assertThat(prs.get(0).exerciseName()).isEqualTo("杠铃卧推");
            assertThat(prs.get(0).previousBest()).as("之前没有成绩").isNull();
            assertThat(prs.get(0).improvement()).isNull();
        }

        @Test
        @DisplayName("超过历史最好成绩 → PR，并给出提升幅度")
        void beatingHistoryIsARecord() {
            // 第一场：60 × 8 → e1RM = 60 × 1.2667 = 76.00
            Long first = startSession("pr-a", LocalDate.of(2026, 9, 14));
            record(first, firstExerciseId(first), 1, SetType.WORKING, "60", 8);
            sessionService.finish(USER, first, 3600, null);

            // 第二场：70 × 8 → e1RM = 88.67
            Long second = startSession("pr-b", LocalDate.of(2026, 9, 21));
            record(second, firstExerciseId(second), 1, SetType.WORKING, "70", 8);

            List<SessionSummaryResponse.PersonalRecordItem> prs =
                    summaryService.summary(USER, second).personalRecords();

            assertThat(prs).hasSize(1);
            assertThat(prs.get(0).e1rm()).isEqualByComparingTo("88.67");
            assertThat(prs.get(0).previousBest()).isEqualByComparingTo("76.00");
            assertThat(prs.get(0).improvement()).isEqualByComparingTo("12.67");
        }

        @Test
        @DisplayName("没超过历史最好成绩就不是 PR")
        void notBeatingHistoryIsNotARecord() {
            Long first = startSession("pr-c", LocalDate.of(2026, 9, 14));
            record(first, firstExerciseId(first), 1, SetType.WORKING, "70", 8);
            sessionService.finish(USER, first, 3600, null);

            Long second = startSession("pr-d", LocalDate.of(2026, 9, 21));
            record(second, firstExerciseId(second), 1, SetType.WORKING, "60", 8);

            assertThat(summaryService.summary(USER, second).personalRecords()).isEmpty();
        }

        @Test
        @DisplayName("⭐ 回看旧训练时，PR 只看那之前的成绩")
        void historicalPrIsScopedToThatMoment() {
            // 9/14 那场必须仍然是 PR —— 即使 9/21 练得更重。
            //
            // 如果查询用「排除本次会话」而不是「早于本次」，
            // 后来的成绩会把当时的 PR 抹掉，用户回看历史会看到
            // 「那天一个 PR 都没有」，而当时他明明突破了。
            Long old = startSession("pr-old", LocalDate.of(2026, 9, 14));
            record(old, firstExerciseId(old), 1, SetType.WORKING, "60", 8);
            sessionService.finish(USER, old, 3600, null);

            Long later = startSession("pr-new", LocalDate.of(2026, 9, 21));
            record(later, firstExerciseId(later), 1, SetType.WORKING, "90", 8);
            sessionService.finish(USER, later, 3600, null);

            assertThat(summaryService.summary(USER, old).personalRecords())
                    .as("9/14 是第一次做，当时就是 PR")
                    .hasSize(1);
        }
    }

    // ==================================================================
    // 三、与上次对比
    // ==================================================================

    @Test
    @DisplayName("与上一次同训练日对比，给出容量与逐动作差值")
    void comparesWithPreviousSameDay() {
        Long first = startSession("cmp-a", LocalDate.of(2026, 9, 14));
        record(first, firstExerciseId(first), 1, SetType.WORKING, "60", 10);
        sessionService.finish(USER, first, 3000, null);

        Long second = startSession("cmp-b", LocalDate.of(2026, 9, 21));
        record(second, firstExerciseId(second), 1, SetType.WORKING, "65", 10);
        record(second, firstExerciseId(second), 2, SetType.WORKING, "65", 8);

        SessionSummaryResponse.Comparison c = summaryService.summary(USER, second).comparison();

        assertThat(c).isNotNull();
        assertThat(c.previousSessionId()).isEqualTo(first);
        // 上次 600，本次 650 + 520 = 1170
        assertThat(c.volumeDelta()).isEqualByComparingTo("570");
        assertThat(c.workingSetsDelta()).isEqualTo(1);
        assertThat(c.durationSecDelta()).as("上次有时长，本次还没结束").isNull();
        assertThat(c.exercises()).hasSize(1);
        assertThat(c.exercises().get(0).weightDelta()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("第一次练这个训练日时没有对比对象")
    void noComparisonForFirstTime() {
        Long sessionId = startSession("cmp-first", LocalDate.of(2026, 9, 21));
        record(sessionId, firstExerciseId(sessionId), 1, SetType.WORKING, "60", 8);

        assertThat(summaryService.summary(USER, sessionId).comparison()).isNull();
    }

    // ==================================================================
    // 四、历史列表
    // ==================================================================

    @Test
    @DisplayName("历史列表带容量与组数，按时间倒序")
    void historyCarriesStats() {
        Long older = startSession("hist-a", LocalDate.of(2026, 9, 14));
        record(older, firstExerciseId(older), 1, SetType.WORKING, "60", 10);
        sessionService.finish(USER, older, 3000, null);

        Long newer = startSession("hist-b", LocalDate.of(2026, 9, 21));
        record(newer, firstExerciseId(newer), 1, SetType.WORKING, "80", 10);
        sessionService.finish(USER, newer, 3600, null);

        IPage<SessionListItem> page = summaryService.history(USER, 1, 10);
        List<SessionListItem> items = page.getRecords();

        assertThat(items).hasSize(2);
        assertThat(items.get(0).id()).as("最近的在前").isEqualTo(newer);
        assertThat(items.get(0).volume()).isEqualByComparingTo("800");
        assertThat(items.get(0).workingSets()).isEqualTo(1);
        assertThat(items.get(0).date()).isEqualTo(LocalDate.of(2026, 9, 21));
        assertThat(items.get(0).exerciseCount()).isEqualTo(1);

        assertThat(items.get(1).volume()).isEqualByComparingTo("600");
    }

    // ==================================================================
    // 五、★ 契约测试：SQL 里的 e1RM 必须等于 Java 里的
    // ==================================================================

    @Test
    @DisplayName("⭐ 契约：SQL 聚合的 e1RM 与 Java 纯函数算出的完全一致")
    void sqlE1rmMatchesJavaE1rm() {
        // e1RM 公式有两份实现：
        //   TrainingMetrics.e1rm          —— 权威实现（Java，纯函数）
        //   SetRecordMapper.bestE1rmBefore —— 性能考虑（SQL 聚合，扫全历史）
        //
        // 两份必须算出同一个数。公式一改而另一处没跟上，
        // 表现是「训练总结说破纪录了，PR 看板说没有」——
        // 用户会开始怀疑所有数据。
        //
        // 这个测试把两份实现对同一批数据的结果钉在一起。

        Long first = startSession("ct-a", LocalDate.of(2026, 9, 14));
        Long exId = firstExerciseId(first);
        record(first, exId, 1, SetType.WORKING, "60", 8);     // e1RM 76.00
        record(first, exId, 2, SetType.WORKING, "65", 5);     // e1RM 75.83
        record(first, exId, 3, SetType.WARMUP, "100", 3);     // 热身，不算
        sessionService.finish(USER, first, 3600, null);

        Long second = startSession("ct-b", LocalDate.of(2026, 9, 21));
        record(second, firstExerciseId(second), 1, SetType.WORKING, "70", 20);  // >12 次，不算
        sessionService.finish(USER, second, 3600, null);

        // ---- SQL 侧 ----
        List<SetRecordMapper.ExerciseBestE1rm> sqlResult = setRecordMapper.bestE1rmBefore(
                USER, List.of(benchId), LocalDateTime.of(2026, 9, 21, 0, 0));

        assertThat(sqlResult).hasSize(1);
        BigDecimal fromSql = sqlResult.get(0).bestE1rm();

        // ---- Java 侧：把第一场的记录读出来，用纯函数算 ----
        SessionExercise snapshot = sessionExerciseMapper.selectById(exId);
        BigDecimal fromJava = setRecordMapper
                .selectList(new LambdaQueryWrapper<SetRecord>()
                        .eq(SetRecord::getSessionExerciseId, exId))
                .stream()
                .map(r -> TrainingMetrics.bestE1rm(r, snapshot))
                .filter(java.util.Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElseThrow();

        assertThat(fromSql)
                .as("SQL 与 Java 必须算出同一个 e1RM")
                .isEqualByComparingTo(fromJava);

        // 同时确认两份实现都正确执行了那条规则：
        // 60×8 → 76.00 胜过 65×5 → 75.83；热身组和 20 次组都不参与
        assertThat(fromJava).isEqualByComparingTo("76.00");
    }

    // ==================================================================
    // 脚手架
    // ==================================================================

    /**
     * 开始一次训练。
     *
     * <p>⚠️ <b>必须显式给 {@code startedAt}</b>。
     *
     * <p>不给的话它默认是「现在」，于是所有测试会话的开始时间都一样——
     * 而 PR 判定和「与上次对比」都依赖 {@code started_at} 的先后。
     * 三个测试就是这么红的：更早的那场压根不算更早，
     * 于是「历史最好成绩」查出来是空的，第二次训练被当成第一次。
     *
     * <p>（真实客户端会传 startedAt，因为离线训练时服务端不在场。）
     */
    private Long startSession(String clientKey, LocalDate date) {
        SessionDetailResponse s = sessionService.create(USER,
                new SessionCreateRequest(programId, 1, date, clientKey, date.atTime(19, 0)));
        return s.id();
    }

    private Long firstExerciseId(Long sessionId) {
        return sessionExerciseMapper.selectList(
                        new LambdaQueryWrapper<SessionExercise>()
                                .eq(SessionExercise::getSessionId, sessionId))
                .get(0).getId();
    }

    private void record(Long sessionId, Long sessionExerciseId, int setNumber,
                        SetType type, String weight, int reps) {
        setRecordService.recordSet(USER, sessionId, sessionExerciseId, setNumber,
                new SetRecordRequest(type, new BigDecimal(weight), reps, null, null,
                        null, null, null, LocalDateTime.now()));
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
