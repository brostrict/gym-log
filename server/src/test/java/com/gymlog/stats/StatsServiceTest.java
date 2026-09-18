package com.gymlog.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.program.ProgramService;
import com.gymlog.program.TargetWeightType;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.stats.dto.ExerciseE1rmResponse;
import com.gymlog.stats.dto.PrBoardResponse;
import com.gymlog.stats.dto.WeeklyStatsResponse;
import com.gymlog.training.SessionExercise;
import com.gymlog.training.SessionExerciseMapper;
import com.gymlog.training.SessionService;
import com.gymlog.training.SetRecordService;
import com.gymlog.training.SetType;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SessionDetailResponse;
import com.gymlog.training.dto.SetRecordRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 统计聚合。
 *
 * <p>⚠️ <b>所有日期都是写死的常量，且会话的 {@code startedAt} 显式给出。</b>
 * 不给的话它默认是「现在」，所有会话挤进同一周——按周聚合的断言会全错，
 * 而失败信息看起来像聚合算法有问题。
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class StatsServiceTest {

    private static final Long USER = 1L;

    /** 2026-09-14 是**周一** */
    private static final LocalDate W1 = LocalDate.of(2026, 9, 14);
    private static final LocalDate W2 = W1.plusWeeks(1);   // 这一周故意不练
    private static final LocalDate W3 = W1.plusWeeks(2);
    private static final LocalDate TODAY = W3.plusDays(2); // 周三

    @Autowired private StatsService statsService;
    @Autowired private SessionService sessionService;
    @Autowired private SetRecordService setRecordService;
    @Autowired private ProgramService programService;
    @Autowired private SessionExerciseMapper sessionExerciseMapper;
    @Autowired private ExerciseMapper exerciseMapper;

    private Long programId;
    private Long benchId;

    @BeforeEach
    void setUp() {
        benchId = findExercise("杠铃卧推").getId();
        programId = programService.create(USER, new ProgramCreateRequest(
                "统计验证计划", null, 0, W1,
                List.of(new ProgramCreateRequest.WeekRequest(
                        1, 3, BigDecimal.ZERO, 0, false, null)),
                List.of(new ProgramCreateRequest.DayRequest(1, "推日", false, null,
                        List.of(new ProgramCreateRequest.PrescriptionRequest(
                                benchId, 1, null, null, 3, 8, 10, 150,
                                TargetWeightType.ABSOLUTE, new BigDecimal("60"),
                                null, null, null, null, null, null))))));
    }

    // ==================================================================

    @Nested
    @DisplayName("周序列")
    class Weekly {

        @Test
        @DisplayName("★ 拉伸不进肌群周组数 —— 它和「10–20 组/周」的增肌区间不可比")
        void stretchDoesNotLeakIntoMuscleSets() {
            // 这条守的是**调用点**，不是枚举本身。
            //
            // MuscleGroupTest 已经保证 isMuscle() 正确，但如果 StatsService
            // 哪天把 MuscleGroup.muscles() 写回 values()，没有测试会红——
            // 而后果是「拉伸」出现在肌群平衡图里，和增肌参考区间并列。
            //
            // 这个错误不会以任何形式报出来。所以必须有一条端到端的断言。
            Long stretchId = findExercise("腘绳肌拉伸").getId();

            Long stretchProgram = programService.create(USER, new ProgramCreateRequest(
                    "拉伸验证计划", null, 0, W1,
                    List.of(new ProgramCreateRequest.WeekRequest(
                            1, 3, BigDecimal.ZERO, 0, false, null)),
                    List.of(new ProgramCreateRequest.DayRequest(1, "放松日", false, null,
                            List.of(new ProgramCreateRequest.PrescriptionRequest(
                                    stretchId, 1, null, null, 3, null, null, 60,
                                    TargetWeightType.ABSOLUTE, null,
                                    null, null, 30, null, null, List.of()))))));

            // 直接练这个计划（不经过 this.programId）
            SessionDetailResponse s = sessionService.create(USER,
                    new SessionCreateRequest(stretchProgram, 1, W1, "stretch-w1",
                            W1.atTime(19, 0)));
            Long se = firstExerciseId(s.id());
            for (int n = 1; n <= 3; n++) {
                setRecordService.recordSet(USER, s.id(), se, n,
                        new SetRecordRequest(SetType.WORKING, null, null, 30, null,
                                null, null, null, W1.atTime(19, n * 3)));
            }
            finish(s.id());

            WeeklyStatsResponse r = statsService.weekly(USER, W1, W1.plusDays(6), TODAY);
            var bucket = r.weeks().get(0);

            assertThat(bucket.muscleSets())
                    .as("肌群图必须固定 6 项——多一项会让客户端的颜色映射错位")
                    .hasSize(6);
            assertThat(bucket.muscleSets())
                    .extracting(WeeklyStatsResponse.MuscleSets::muscle)
                    .doesNotContain("STRETCH", "WARMUP");
            assertThat(bucket.muscleSets())
                    .allSatisfy(m -> assertThat(m.sets())
                            .as("%s 不该有组数——这一周只做了拉伸", m.muscle())
                            .isZero());
        }

        @Test
        @DisplayName("★ 空周也要有桶，且各项为 0（METRICS 4.6：不跳过）")
        void emptyWeekIsZeroFilledNotSkipped() {
            trainOn(W1, "w1", "60", 8, 8, 8);
            trainOn(W3, "w3", "60", 8, 8, 8);

            WeeklyStatsResponse r = statsService.weekly(
                    USER, W1, W3.plusDays(6), TODAY);

            assertThat(r.weeks()).extracting(WeeklyStatsResponse.WeekBucket::weekStart)
                    .containsExactly(W1, W2, W3);

            var middle = r.weeks().get(1);
            assertThat(middle.sessionCount()).as("中间那周没练").isZero();
            assertThat(middle.volume()).isEqualByComparingTo("0");
            assertThat(middle.workingSets()).isZero();
            // 关键：桶存在。跳过的话「中断三周」会被画成「连续三周」，
            // 正好掩盖了最该看见的东西。
        }

        @Test
        @DisplayName("★ 周三的训练归到**本周一**，不是下周一")
        void wednesdayBelongsToThisMonday() {
            trainOn(W1.plusDays(2), "wed", "60", 8, 8, 8);   // 周三

            WeeklyStatsResponse r = statsService.weekly(USER, W1, W1.plusDays(6), TODAY);

            assertThat(r.weeks()).hasSize(1);
            assertThat(r.weeks().get(0).weekStart()).isEqualTo(W1);
            assertThat(r.weeks().get(0).sessionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("周桶按时间升序（用 HashMap 的话顺序随机，X 轴会画成锯齿）")
        void weeksAreAscending() {
            trainOn(W3, "w3", "60", 8, 8, 8);
            trainOn(W1, "w1", "60", 8, 8, 8);

            WeeklyStatsResponse r = statsService.weekly(USER, W1, W3.plusDays(6), TODAY);
            assertThat(r.weeks()).extracting(WeeklyStatsResponse.WeekBucket::weekStart)
                    .isSorted();
        }

        @Test
        @DisplayName("★ streak 由服务端算，客户端不自己数（口径只有一份）")
        void streakComesFromServer() {
            // 只练了当前周，上周是空的 → 当前这一段就是 1 周
            trainOn(W3, "s1", "60", 8, 8, 8);

            assertThat(statsService.weekly(USER, W1, W3.plusDays(6), TODAY).currentStreak())
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("连续两周都练 → streak = 2")
        void streakCountsConsecutiveWeeks() {
            trainOn(W2, "s1", "60", 8, 8, 8);
            trainOn(W3, "s2", "60", 8, 8, 8);

            assertThat(statsService.weekly(USER, W1, W3.plusDays(6), TODAY).currentStreak())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("当前周被标记出来（供客户端半透明显示）")
        void currentWeekFlag() {
            WeeklyStatsResponse r = statsService.weekly(USER, W1, W3.plusDays(6), TODAY);
            assertThat(r.weeks())
                    .filteredOn(WeeklyStatsResponse.WeekBucket::currentWeek)
                    .extracting(WeeklyStatsResponse.WeekBucket::weekStart)
                    .containsExactly(W3);
        }

        @Test
        @DisplayName("肌群组数固定 6 项、含 0（缺项会让客户端颜色映射错位）")
        void muscleSetsAlwaysSixEntries() {
            trainOn(W1, "w1", "60", 8, 8, 8);

            var bucket = statsService.weekly(USER, W1, W1.plusDays(6), TODAY).weeks().get(0);
            assertThat(bucket.muscleSets()).hasSize(6);
            assertThat(bucket.muscleSets()).extracting(WeeklyStatsResponse.MuscleSets::muscle)
                    .containsExactly("CHEST", "BACK", "LEGS", "SHOULDERS", "ARMS", "CORE");

            var chest = bucket.muscleSets().stream()
                    .filter(m -> m.muscle().equals("CHEST")).findFirst().orElseThrow();
            assertThat(chest.sets()).as("卧推 3 组都算胸").isEqualTo(3);
            var legs = bucket.muscleSets().stream()
                    .filter(m -> m.muscle().equals("LEGS")).findFirst().orElseThrow();
            assertThat(legs.sets()).as("没练腿，是 0 不是缺项").isZero();
        }
    }

    @Nested
    @DisplayName("组数与容量口径")
    class Metrics {

        @Test
        @DisplayName("★ 热身组不计入组数，也不算进肌群（走 TrainingMetrics.isWorkingSet）")
        void warmupExcluded() {
            Long session = start(W1, "warm");
            Long se = firstExerciseId(session);
            record(session, se, 1, SetType.WARMUP, "40", 12);
            record(session, se, 2, SetType.WORKING, "60", 8);
            record(session, se, 3, SetType.WORKING, "60", 8);
            finish(session);

            var bucket = statsService.weekly(USER, W1, W1.plusDays(6), TODAY).weeks().get(0);
            assertThat(bucket.workingSets()).isEqualTo(2);
            assertThat(bucket.volume()).as("只有正式组：60×8×2").isEqualByComparingTo("960");
            assertThat(bucket.muscleSets().stream()
                    .filter(m -> m.muscle().equals("CHEST")).findFirst().orElseThrow().sets())
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("符合率：计划 3 组实际 2 组 → 66.7")
        void compliance() {
            Long session = start(W1, "comp");
            Long se = firstExerciseId(session);
            record(session, se, 1, SetType.WORKING, "60", 8);
            record(session, se, 2, SetType.WORKING, "60", 8);
            finish(session);

            var bucket = statsService.weekly(USER, W1, W1.plusDays(6), TODAY).weeks().get(0);
            assertThat(bucket.complianceRate()).isEqualByComparingTo("66.7");
        }
    }

    @Nested
    @DisplayName("单动作 e1RM 曲线")
    class E1rm {

        @Test
        @DisplayName("自重动作返回 supported=false，而不是一张空图")
        void nonWeightRepsNotSupported() {
            Long pullUp = findExercise("引体向上").getId();

            ExerciseE1rmResponse r = statsService.e1rm(USER, pullUp, W1, W3);

            assertThat(r.supported()).isFalse();
            assertThat(r.points()).isEmpty();
            assertThat(r.metricType()).isEqualTo("REPS_ONLY");
        }

        @Test
        @DisplayName("取每次训练的**最佳组**，不是全部组平均（METRICS 3.3）")
        void takesBestSetPerSession() {
            Long session = start(W1, "e1rm");
            Long se = firstExerciseId(session);
            record(session, se, 1, SetType.WORKING, "60", 8);    // 60×(1+8/30)  = 76.00
            record(session, se, 2, SetType.WORKING, "70", 5);    // 70×(1+5/30)  = 81.67
            record(session, se, 3, SetType.WORKING, "65", 6);    // 65×(1+6/30)  = 78.00
            finish(session);

            ExerciseE1rmResponse r = statsService.e1rm(USER, benchId, W1, W1.plusDays(6));

            assertThat(r.supported()).isTrue();
            assertThat(r.points()).hasSize(1);
            assertThat(r.points().get(0).bestE1rm()).isEqualByComparingTo("81.67");
            assertThat(r.points().get(0).sets()).as("三次的散点都在").hasSize(3);
            assertThat(r.allTimeBest()).as("历史最高 = 本次最佳").isEqualByComparingTo("81.67");
        }

        @Test
        @DisplayName("★ 全部组 reps > 12 的那次不参与曲线，但训练记录本身正常")
        void highRepsSessionExcluded() {
            Long session = start(W1, "hi");
            Long se = firstExerciseId(session);
            record(session, se, 1, SetType.WORKING, "40", 15);
            record(session, se, 2, SetType.WORKING, "40", 20);
            finish(session);

            ExerciseE1rmResponse r = statsService.e1rm(USER, benchId, W1, W1.plusDays(6));

            assertThat(r.points()).as("Epley 在 12 次以上严重高估，整次不取点").isEmpty();
            assertThat(r.allTimeBest()).isNull();
        }
    }

    @Nested
    @DisplayName("PR 看板")
    class Prs {

        @Test
        @DisplayName("最大重量与最佳 e1RM 分开记录，不合并成一个「最强」")
        void twoMetricsKeptSeparate() {
            // 第一场：大重量低次数 → 最大重量高
            Long s1 = start(W1, "pr1");
            Long se1 = firstExerciseId(s1);
            record(s1, se1, 1, SetType.WORKING, "100", 3);
            finish(s1);

            // 第二场：中等重量高次数 → e1RM 更高
            Long s2 = start(W3, "pr2");
            Long se2 = firstExerciseId(s2);
            record(s2, se2, 1, SetType.WORKING, "80", 12);
            finish(s2);

            PrBoardResponse r = statsService.prs(USER, TODAY);

            var maxWeight = r.records().stream()
                    .filter(c -> c.metric().equals("MAX_WEIGHT")).findFirst().orElseThrow();
            var bestE1rm = r.records().stream()
                    .filter(c -> c.metric().equals("BEST_E1RM")).findFirst().orElseThrow();

            assertThat(maxWeight.value()).isEqualByComparingTo("100.00");
            assertThat(maxWeight.achievedOn()).isEqualTo(W1);
            // 80×(1+12/30) = 112.00 > 100×(1+3/30) = 110.00
            assertThat(bestE1rm.value()).isEqualByComparingTo("112.00");
            assertThat(bestE1rm.achievedOn()).isEqualTo(W3);
        }

        @Test
        @DisplayName("★ 只有一场训练 → 标记「首次记录」（METRICS 7.4）")
        void firstTimeFlag() {
            Long s = start(W1, "first");
            Long se = firstExerciseId(s);
            record(s, se, 1, SetType.WORKING, "60", 8);
            finish(s);

            PrBoardResponse r = statsService.prs(USER, TODAY);

            assertThat(r.records()).isNotEmpty();
            assertThat(r.records()).allMatch(PrBoardResponse.PrCard::firstTime);
        }

        @Test
        @DisplayName("★ 同一数值多次达成只保留**最早**一次（METRICS 7.4）")
        void earliestAchievementWins() {
            recordOn(W1, "tie1", "80", 5);
            recordOn(W3, "tie2", "80", 5);   // 同样的成绩，更晚

            PrBoardResponse r = statsService.prs(USER, TODAY);

            var maxWeight = r.records().stream()
                    .filter(c -> c.metric().equals("MAX_WEIGHT")).findFirst().orElseThrow();
            assertThat(maxWeight.achievedOn()).isEqualTo(W1);
            assertThat(maxWeight.firstTime()).as("首次就创了纪录").isTrue();
        }

        @Test
        @DisplayName("按达成日期倒序（最近的在前）")
        void sortedByDateDesc() {
            recordOn(W1, "o1", "100", 3);
            recordOn(W3, "o2", "80", 12);   // e1RM 更高，日期更近

            PrBoardResponse r = statsService.prs(USER, TODAY);
            // ⚠️ 是**倒序**（METRICS 7.3「按达成日期倒序，最近的在前」），
            // 不是 isSorted()——那个查的是升序，会把正确实现判成失败。
            assertThat(r.records()).extracting(PrBoardResponse.PrCard::achievedOn)
                    .isSortedAccordingTo(java.util.Comparator.reverseOrder());
            assertThat(r.records().get(0).achievedOn()).as("倒序 → 最近的在最前").isEqualTo(W3);
        }

        @Test
        @DisplayName("没有任何记录 → 空列表，不抛异常")
        void noData() {
            assertThat(statsService.prs(999_999L, TODAY).records()).isEmpty();
        }
    }

    // ==================================================================
    // 脚手架
    // ==================================================================

    /** 在指定日期练一场：卧推 3 组，重量和次数由调用方给 */
    private void trainOn(LocalDate date, String key, String weight,
                         int r1, int r2, int r3) {
        Long session = start(date, key);
        Long se = firstExerciseId(session);
        record(session, se, 1, SetType.WORKING, weight, r1);
        record(session, se, 2, SetType.WORKING, weight, r2);
        record(session, se, 3, SetType.WORKING, weight, r3);
        finish(session);
    }

    private void recordOn(LocalDate date, String key, String weight, int reps) {
        Long session = start(date, key);
        record(session, firstExerciseId(session), 1, SetType.WORKING, weight, reps);
        finish(session);
    }

    /**
     * 造一场**已完成**的训练。
     *
     * <p>⚠️ 两个坑叠在一起，而且都只表现为「聚合结果全是 0」：
     *
     * <ol>
     *   <li><b>必须显式给 {@code startedAt}</b>。不给的话默认是「现在」，
     *       所有会话挤进同一周——按周聚合的断言会全错，
     *       而失败信息看起来像聚合算法有问题。</li>
     *   <li><b>必须调 {@code finish()}</b>。{@code create()} 建出来的会话初始状态是
     *       {@code IN_PROGRESS}（{@code SessionService.java:93}），而统计按
     *       {@code METRICS 5.1} 只认 {@code COMPLETED}——
     *       不结束就一条都聚合不到。</li>
     * </ol>
     *
     * <p>第二条踩过：15 个测试里 9 个红，全是「expected 2 but was 0」这种形状，
     * 而零填充那几个用例照样绿（它们不需要真有会话）。
     */
    private Long start(LocalDate date, String clientKey) {
        SessionDetailResponse s = sessionService.create(USER,
                new SessionCreateRequest(programId, 1, date, clientKey, date.atTime(19, 0)));
        return s.id();
    }

    /**
     * 结束会话。
     *
     * <p><b>顺序不能反</b>：{@code recordSet} 要求会话处于 {@code IN_PROGRESS}
     * （{@code SetRecordService} 里有守卫），所以必须先记完组再结束。
     * 反过来的话记录会被拒，而报错信息是「会话已结束」——
     * 看起来像测试写错了流程，实际只是顺序问题。
     */
    private void finish(Long sessionId) {
        sessionService.finish(USER, sessionId, null, null);
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
