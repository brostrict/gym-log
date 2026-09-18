package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.body.BodyMetricType;
import com.gymlog.body.BodyService;
import com.gymlog.body.MetricCondition;
import com.gymlog.body.dto.BodyMetricRequest;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.program.ProgramService;
import com.gymlog.program.TargetWeightType;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.program.dto.ProgramStructureRequest;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SessionDetailResponse;
import com.gymlog.training.dto.SessionSummaryResponse;
import com.gymlog.training.dto.SetRecordRequest;
import com.gymlog.training.dto.TodayWorkoutResponse;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 会话创建与快照。
 *
 * <p>三个重点：
 * <ol>
 *   <li><b>快照存的是展开后的值</b>——不是「第 5 周 +5%」这种修饰</li>
 *   <li><b>幂等与「只能有一个进行中会话」</b>——离线重试的基础</li>
 *   <li><b>★ 训练日轮转闭环</b>——完成一场，下一场推荐换到下一个训练日</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class SessionServiceTest {

    private static final Long USER = 1L;
    private static final LocalDate START = LocalDate.of(2026, 9, 14);
    /** 落在第 2 周（9/14 起算第 8 天） */
    private static final LocalDate DAY = LocalDate.of(2026, 9, 21);

    @Autowired private SessionService sessionService;
    @Autowired private SetRecordService setRecordService;
    @Autowired private TodayWorkoutService todayWorkoutService;
    @Autowired private ProgramService programService;
    @Autowired private WorkoutSessionMapper sessionMapper;
    @Autowired private SessionCounter sessionCounter;
    @Autowired private SessionExerciseMapper sessionExerciseMapper;
    @Autowired private ExerciseMapper exerciseMapper;
    @Autowired private BodyService bodyService;
    @Autowired private SessionSummaryService summaryService;

    private Long benchId;
    private Long squatId;

    @BeforeEach
    void setUp() {
        benchId = findExercise("杠铃卧推").getId();
        squatId = findExercise("杠铃深蹲").getId();
    }

    // ==================================================================
    // 一、快照内容
    // ==================================================================

    @Test
    @DisplayName("★ 快照存的是展开后的具体值，不是「第几周 +几%」这种修饰")
    void snapshotStoresExpandedValues() {
        // 第 2 周 +10%：60kg → 66kg
        Long programId = twoDayProgram(List.of(week(1, "0"), week(2, "10")));

        SessionDetailResponse session = sessionService.create(USER,
                new SessionCreateRequest(programId, 1, DAY, "k-expand", null));

        assertThat(session.weekNumber()).isEqualTo(2);
        assertThat(session.weightAdjustPct()).isEqualByComparingTo("10");
        assertThat(session.exercises()).hasSize(1);

        SessionDetailResponse.ExerciseItem bench = session.exercises().get(0);
        assertThat(bench.exerciseName()).isEqualTo("杠铃卧推");
        assertThat(bench.primaryMuscle()).isEqualTo("CHEST");
        assertThat(bench.targetSets()).isEqualTo(3);

        // ⚠️ 关键断言：66 是算好的，不是 60 + 「+10%」这个修饰
        assertThat(bench.sets()).hasSize(3);
        assertThat(bench.sets())
                .allSatisfy(s -> assertThat(s.weight()).isEqualByComparingTo("66"));
    }

    @Test
    @DisplayName("减量周的快照反映减量后的组数与重量")
    void snapshotReflectsDeload() {
        Long programId = twoDayProgram(List.of(
                week(1, "0"), week(2, "10", 0, false), week(3, "-40", -1, true)));

        // 9/28 = START + 14 天 → 第 3 周
        SessionDetailResponse session = sessionService.create(USER,
                new SessionCreateRequest(programId, 1, LocalDate.of(2026, 9, 28), "k-deload", null));

        assertThat(session.weekNumber()).isEqualTo(3);
        assertThat(session.deload()).isTrue();
        // 3 组 - 1 = 2 组；60 × 0.6 = 36kg
        assertThat(session.exercises().get(0).sets()).hasSize(2);
        assertThat(session.exercises().get(0).sets().get(0).weight())
                .isEqualByComparingTo("36");
    }

    @Test
    @DisplayName("★ 创建会话后改计划，会话内容完全不变")
    void sessionIsImmuneToProgramEdits() {
        Long programId = twoDayProgram(List.of(week(1, "0")));

        SessionDetailResponse session = sessionService.create(USER,
                new SessionCreateRequest(programId, 1, DAY, "k-immune", null));
        assertThat(session.exercises().get(0).sets().get(0).weight())
                .isEqualByComparingTo("60");

        // 大改计划：换成深蹲 5×5 @ 120kg，训练日改名
        programService.updateStructure(USER, programId, new ProgramStructureRequest(
                List.of(week(1, "0")),
                List.of(day(1, "腿部日", prescription(squatId, 5, 5, 5, 240, "120"))),
                1));

        SessionDetailResponse after = sessionService.detail(USER, session.id(), false);

        assertThat(after.dayName()).as("训练日改名了，快照仍是「推日」").isEqualTo("推日");
        assertThat(after.exercises()).hasSize(1);
        assertThat(after.exercises().get(0).exerciseName())
                .as("动作从卧推换成了深蹲，快照仍是卧推")
                .isEqualTo("杠铃卧推");
        assertThat(after.exercises().get(0).sets().get(0).weight())
                .as("重量改成 120kg 了，快照仍是 60kg")
                .isEqualByComparingTo("60");
    }

    // ==================================================================
    // 二、幂等与并发
    // ==================================================================

    @Nested
    @DisplayName("幂等与「只能有一个进行中会话」")
    class Idempotency {

        @Test
        @DisplayName("同一 clientKey 重复创建，返回原会话而不是新建")
        void sameClientKeyReturnsExisting() {
            Long programId = twoDayProgram(List.of(week(1, "0")));

            SessionDetailResponse first = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-retry", null));
            SessionDetailResponse second = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-retry", null));

            assertThat(second.id()).isEqualTo(first.id());
            assertThat(second.resumed()).isTrue();

            assertThat(countMySessions("k-retry"))
                    .as("重试 3 次也只该有一条")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("已有进行中的会话时，新请求返回它而不是新建")
        void returnsExistingActiveSession() {
            Long programId = twoDayProgram(List.of(week(1, "0")));

            SessionDetailResponse first = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-a", null));

            // 不同 key，但同一个人不可能同时练两场
            SessionDetailResponse second = sessionService.create(USER,
                    new SessionCreateRequest(programId, 2, DAY, "k-b", null));

            assertThat(second.id()).isEqualTo(first.id());
            assertThat(second.resumed()).isTrue();
            assertThat(countMySessions("k-a", "k-b")).isEqualTo(1);
        }

        @Test
        @DisplayName("上一场结束后可以开始新的一场")
        void canStartNewAfterFinish() {
            Long programId = twoDayProgram(List.of(week(1, "0")));

            SessionDetailResponse first = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-1", null));
            sessionService.finish(USER, first.id(), 3600, null);

            SessionDetailResponse second = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-2", null));

            assertThat(second.id()).isNotEqualTo(first.id());
            assertThat(second.resumed()).isFalse();
            assertThat(countMySessions("k-1", "k-2")).isEqualTo(2);
        }

        @Test
        @DisplayName("放弃后也可以开始新的一场")
        void canStartNewAfterAbandon() {
            Long programId = twoDayProgram(List.of(week(1, "0")));

            SessionDetailResponse first = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-ab-1", null));
            sessionService.abandon(USER, first.id());

            SessionDetailResponse second = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-ab-2", null));

            assertThat(second.id()).isNotEqualTo(first.id());
        }

        @Test
        @DisplayName("别人的进行中会话不会影响我")
        void otherUsersActiveSessionDoesNotBlockMe() {
            Long programId = twoDayProgram(List.of(week(1, "0")));

            // 别人先开一场（用临时训练，因为他不拥有这个计划）
            sessionService.create(999L,
                    new SessionCreateRequest(null, null, DAY, "k-theirs", null));

            // 我这边不受影响
            SessionDetailResponse mine = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-mine", null));

            assertThat(mine.resumed()).isFalse();
            assertThat(mine.exercises()).hasSize(1);
        }
    }

    // ==================================================================
    // 三、★ 训练日轮转闭环（2.15 留下的缺口在这里补上）
    // ==================================================================

    @Test
    @DisplayName("★ 完成一场训练后，下一次推荐换到下一个训练日")
    void completingASessionAdvancesTheRotation() {
        Long programId = twoDayProgram(List.of(week(1, "0")));

        // 一次没练过 → 推荐第 1 个训练日，已完成 0 次
        TodayWorkoutResponse before = todayWorkoutService.today(USER, programId, DAY, null);
        assertThat(before.completedSessions()).isZero();
        assertThat(before.dayName()).isEqualTo("推日");

        // 练完一场
        SessionDetailResponse s1 = sessionService.create(USER,
                new SessionCreateRequest(programId, null, DAY, "k-rot-1", null));
        assertThat(s1.dayName()).isEqualTo("推日");
        sessionService.finish(USER, s1.id(), 3600, null);

        // 再打开首页 → 该练第 2 个训练日了
        TodayWorkoutResponse after = todayWorkoutService.today(USER, programId, DAY, null);
        assertThat(after.completedSessions()).isEqualTo(1);
        assertThat(after.dayName())
                .as("练完推日，下一次该是腿日")
                .isEqualTo("腿日");

        // 再练完一场 → 循环回第 1 个训练日
        SessionDetailResponse s2 = sessionService.create(USER,
                new SessionCreateRequest(programId, null, DAY, "k-rot-2", null));
        sessionService.finish(USER, s2.id(), 3600, null);

        TodayWorkoutResponse back = todayWorkoutService.today(USER, programId, DAY, null);
        assertThat(back.completedSessions()).isEqualTo(2);
        assertThat(back.dayName())
                .as("两个训练日，练完两场就绕回第一个")
                .isEqualTo("推日");
    }

    @Test
    @DisplayName("放弃的训练不推进轮转")
    void abandonedSessionsDoNotAdvanceRotation() {
        Long programId = twoDayProgram(List.of(week(1, "0")));

        SessionDetailResponse s = sessionService.create(USER,
                new SessionCreateRequest(programId, null, DAY, "k-abandon-rot", null));
        sessionService.abandon(USER, s.id());

        assertThat(sessionCounter.completedCount(USER, programId)).isZero();
        assertThat(todayWorkoutService.today(USER, programId, DAY, null).dayName())
                .as("练到一半放弃，下次还是练同一个训练日")
                .isEqualTo("推日");
    }

    @Test
    @DisplayName("临时训练不影响任何计划的轮转")
    void adHocSessionsDoNotAffectRotation() {
        Long programId = twoDayProgram(List.of(week(1, "0")));

        SessionDetailResponse adHoc = sessionService.create(USER,
                new SessionCreateRequest(null, null, DAY, "k-adhoc", null));
        sessionService.finish(USER, adHoc.id(), 1800, null);

        assertThat(adHoc.programId()).isNull();
        assertThat(adHoc.exercises()).isEmpty();
        assertThat(sessionCounter.completedCount(USER, programId)).isZero();
    }

    // ==================================================================
    // 四、★ 训练时长的推算
    // ==================================================================

    @Test
    @DisplayName("★ 时长 = 最后一组的完成时刻 − 开始时刻，不是「点结束的时刻」")
    void durationComesFromLastSetNotFromFinishTap() {
        // 19:00 开始
        SessionDetailResponse s = sessionService.create(USER, new SessionCreateRequest(
                twoDayProgram(List.of(week(1, "0"))), 1, DAY, "k-dur", DAY.atTime(19, 0)));
        Long exerciseId = firstExerciseId(s.id());

        // 第 1 组 19:05 做完，第 2 组 19:30 做完
        recordSet(s.id(), exerciseId, 1, DAY.atTime(19, 5));
        recordSet(s.id(), exerciseId, 2, DAY.atTime(19, 30));

        // 用户把手机揣兜里，两小时后（21:30）才想起来点结束。
        // 客户端此时会报一个巨大的值——**必须被忽略**。
        sessionService.finish(USER, s.id(), 9000, null);

        Integer duration = sessionMapper.selectById(s.id()).getDurationSec();

        assertThat(duration)
                .as("19:00 → 19:30 是 30 分钟；绝不能是客户端报的 9000 秒")
                .isEqualTo(1800);
    }

    @Test
    @DisplayName("一组都没做时，用客户端上报的时长兜底")
    void durationFallsBackToClientValueWhenNoSets() {
        SessionDetailResponse s = sessionService.create(USER, new SessionCreateRequest(
                twoDayProgram(List.of(week(1, "0"))), 1, DAY, "k-dur-empty", DAY.atTime(19, 0)));

        // 没有任何组记录
        sessionService.finish(USER, s.id(), 45, null);

        assertThat(sessionMapper.selectById(s.id()).getDurationSec())
                .as("没有组记录可推算时才用客户端的值")
                .isEqualTo(45);
    }

    @Test
    @DisplayName("组记录的时刻早于开始时刻时夹到 0，不出现负数")
    void durationNeverNegative() {
        SessionDetailResponse s = sessionService.create(USER, new SessionCreateRequest(
                twoDayProgram(List.of(week(1, "0"))), 1, DAY, "k-dur-skew", DAY.atTime(19, 0)));
        Long exerciseId = firstExerciseId(s.id());

        // 设备时钟偏慢：上报的完成时刻早于服务端记录的开始时刻
        recordSet(s.id(), exerciseId, 1, DAY.atTime(18, 50));
        sessionService.finish(USER, s.id(), null, null);

        assertThat(sessionMapper.selectById(s.id()).getDurationSec())
                .as("负数的训练时长比 0 更糟——界面上会显示成乱码")
                .isZero();
    }

    private Long firstExerciseId(Long sessionId) {
        return sessionExerciseMapper.selectList(
                        new LambdaQueryWrapper<SessionExercise>()
                                .eq(SessionExercise::getSessionId, sessionId))
                .get(0).getId();
    }

    private void recordSet(Long sessionId, Long exerciseId, int setNumber,
                           LocalDateTime completedAt) {
        setRecordService.recordSet(USER, sessionId, exerciseId, setNumber,
                new SetRecordRequest(SetType.WORKING, new BigDecimal("60"), 8,
                        null, null, null, null, null, completedAt));
    }

    // ==================================================================
    // 五、自重动作的快照链（AC-7-8）
    // ==================================================================

    @Nested
    @DisplayName("自重动作的容量快照")
    class BodyweightSnapshot {

        /**
         * ⭐ 这几条测的是一个**藏了很久的断链**。
         *
         * <p>{@code V12} 加了 {@code session_exercise.bw_factor} 列，注释写着
         * 「容量 = 体重 × bw_factor × 次数」；{@code TrainingMetrics.setVolume}
         * 一直在读它。缺的是**中间那一段**：展开函数
         * （{@code ExpandedWorkout.ExerciseItem}）根本不带这个字段，
         * 会话快照也就无从拷贝。
         *
         * <p>为什么几个月没被发现：读不到时 {@code setVolume} 返回 <b>0</b>，
         * 而 0 正是 {@code METRICS 4.1}「没记体重就不计容量」的**合法值**。
         * 于是「引体向上容量 0」看起来完全正常——直到 4B 真的记了体重，
         * 它还是 0，才露出来。
         *
         * <p>所以这三条断言刻意分层：先证明快照里**有**这个值，
         * 再证明它**流到了数字上**。只看容量的话，返 0 和「系数没拷过来」
         * 长得一模一样，测不出来。
         */
        @Test
        @DisplayName("★ 会话快照拷贝 bw_factor —— 不拷贝的话自重容量永远是 0")
        void snapshotCopiesBwFactor() {
            Exercise pullUp = findExercise("引体向上");
            assertThat(pullUp.getBwFactor())
                    .as("前置条件：动作库里引体向上有自重系数").isNotNull();

            Long programId = singleDayProgram(pullUp.getId(), 3, 8, 8);

            SessionDetailResponse session = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-bw", DAY.atTime(19, 0)));

            SessionExercise snapshot = sessionExerciseMapper.selectList(
                            new LambdaQueryWrapper<SessionExercise>()
                                    .eq(SessionExercise::getSessionId, session.id()))
                    .get(0);

            assertThat(snapshot.getBwFactor())
                    .as("快照必须带上 bw_factor，否则 TrainingMetrics 读到的永远是 null")
                    .isEqualByComparingTo(pullUp.getBwFactor());
        }

        @Test
        @DisplayName("★ 快照体重 × bw_factor × 次数 真的算进了训练总结（AC-7-8）")
        void bodyweightCountsTowardVolume() {
            Exercise pullUp = findExercise("引体向上");

            // 训练当天早上的体重。**必须早于 startedAt**——
            // weightAsOf 查的是「那一刻之前最近的一次」
            bodyService.record(USER, new BodyMetricRequest(
                    BodyMetricType.WEIGHT, null, new BigDecimal("70.0"),
                    DAY.atTime(7, 0), MetricCondition.FASTED, null));

            Long programId = singleDayProgram(pullUp.getId(), 3, 8, 8);
            SessionDetailResponse session = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-bw-vol", DAY.atTime(19, 0)));

            Long exerciseId = firstExerciseId(session.id());
            for (int n = 1; n <= 3; n++) {
                setRecordService.recordSet(USER, session.id(), exerciseId, n,
                        new SetRecordRequest(SetType.WORKING, null, 8,
                                null, null, null, null, null, DAY.atTime(19, n * 5)));
            }
            setRecordService.updateExerciseStatus(USER, session.id(), exerciseId,
                    SessionExerciseStatus.COMPLETED);
            sessionService.finish(USER, session.id(), 1800, null);

            SessionSummaryResponse summary = summaryService.summary(USER, session.id());

            // 70 × 1.00 × 8 × 3 = 1680
            assertThat(summary.volume())
                    .as("自重动作的容量 = 体重 × bw_factor × 次数")
                    .isEqualByComparingTo("1680");
        }

        @Test
        @DisplayName("没有体重记录时不计入容量 —— 宁可不计，也不拿假体重算（METRICS 4.1）")
        void noWeightMeansZeroVolume() {
            Exercise pullUp = findExercise("引体向上");
            Long programId = singleDayProgram(pullUp.getId(), 3, 8, 8);

            // 刻意不记体重
            SessionDetailResponse session = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-bw-now", DAY.atTime(19, 0)));

            Long exerciseId = firstExerciseId(session.id());
            for (int n = 1; n <= 3; n++) {
                setRecordService.recordSet(USER, session.id(), exerciseId, n,
                        new SetRecordRequest(SetType.WORKING, null, 8,
                                null, null, null, null, null, DAY.atTime(19, n * 5)));
            }
            sessionService.finish(USER, session.id(), 1800, null);

            // 注意这条**和上面那条只差一步**（记没记体重），
            // 所以它不能证明 bw_factor 拷过来了——那由 snapshotCopiesBwFactor 负责。
            // 它守的是另一件事：拿不到体重时必须是 0，而不是某个默认体重。
            assertThat(summaryService.summary(USER, session.id()).volume())
                    .isEqualByComparingTo("0");
        }

        private Long singleDayProgram(Long exerciseId, int sets, int repsMin, int repsMax) {
            return programService.create(USER, new ProgramCreateRequest(
                    "自重验证计划-" + exerciseId, null, 0, START,
                    List.of(week(1, "0")),
                    List.of(day(1, "拉日",
                            new ProgramCreateRequest.PrescriptionRequest(
                                    exerciseId, 1, null, null, sets, repsMin, repsMax, 120,
                                    TargetWeightType.ABSOLUTE, null,
                                    null, null, null, null, null, null)))));
        }
    }

    // ==================================================================
    // 六、状态流转与守卫
    // ==================================================================

    @Nested
    @DisplayName("状态流转")
    class Transitions {

        @Test
        @DisplayName("完成训练记录时长与备注")
        void finishRecordsDurationAndNote() {
            Long programId = twoDayProgram(List.of(week(1, "0")));
            SessionDetailResponse s = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-fin", null));
            assertThat(s.status()).isEqualTo("IN_PROGRESS");

            SessionDetailResponse done = sessionService.finish(USER, s.id(), 3720, "状态不错");

            assertThat(done.status()).isEqualTo("COMPLETED");
            assertThat(done.durationSec()).isEqualTo(3720);
            assertThat(done.note()).isEqualTo("状态不错");
            assertThat(done.finishedAt()).isNotNull();
        }

        @Test
        @DisplayName("重复结束会报错，而不是静默成功")
        void doubleFinishIsRejected() {
            Long programId = twoDayProgram(List.of(week(1, "0")));
            SessionDetailResponse s = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-dbl", null));
            sessionService.finish(USER, s.id(), 3600, null);

            // 静默成功会让客户端以为第一次请求丢了，从而重试出
            // 「已完成但数据被覆盖」的状态
            assertThatThrownBy(() -> sessionService.finish(USER, s.id(), 100, null))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_ALREADY_COMPLETED);
        }

        @Test
        @DisplayName("已完成的会话不能再放弃")
        void cannotAbandonFinishedSession() {
            Long programId = twoDayProgram(List.of(week(1, "0")));
            SessionDetailResponse s = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-fin-ab", null));
            sessionService.finish(USER, s.id(), 3600, null);

            assertThatThrownBy(() -> sessionService.abandon(USER, s.id()))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("计划还没开始时，不指定训练日就练不了（轮转没有意义）")
        void cannotStartWithoutDayWhenNotStarted() {
            Long programId = twoDayProgram(List.of(week(1, "0")));
            LocalDate beforeStart = START.minusDays(3);

            // dayNumber 为 null → 走轮转 → 计划还没开始 → 没有可练的内容
            assertThatThrownBy(() -> sessionService.create(USER,
                    new SessionCreateRequest(programId, null, beforeStart, "k-early", null)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("未开始");
        }

        @Test
        @DisplayName("但显式指定训练日时可以提前开始 —— 用户主动选择优先于日程")
        void explicitDayAllowsStartingEarly() {
            Long programId = twoDayProgram(List.of(week(1, "0")));
            LocalDate beforeStart = START.minusDays(3);

            // 这条行为是**刻意的**，不是漏判：
            //
            //   dayNumber == null  → 「告诉我今天该练什么」→ 要看日程
            //   dayNumber 有值     → 「我知道我要练第几天」→ 用户说了算
            //
            // 挡掉后者会连带挡掉两件合理的事：
            //   ① 「计划下周一开始，但我今天有空想先练一次」
            //   ② 「8 周计划走完了，我想再从头来一轮」
            //
            // 而且响应里**仍然带着 scheduleState**，
            // 客户端想提示「计划已结束」随时可以提示——信息没有丢，
            // 只是不替用户做决定。
            SessionDetailResponse early = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, beforeStart, "k-early-explicit", null));

            assertThat(early.dayName()).isEqualTo("推日");
            assertThat(early.exercises()).hasSize(1);
        }

        @Test
        @DisplayName("别人的会话查不到、改不了")
        void otherUsersSessionIsNotFound() {
            Long programId = twoDayProgram(List.of(week(1, "0")));
            SessionDetailResponse s = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, "k-priv", null));

            assertThatThrownBy(() -> sessionService.detail(999L, s.id(), false))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_NOT_FOUND);

            assertThatThrownBy(() -> sessionService.finish(999L, s.id(), 100, null))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_NOT_FOUND);
        }

        @Test
        @DisplayName("没有进行中会话时 active 返回 null")
        void activeIsNullWhenNothingInProgress() {
            assertThat(sessionService.findActive(USER)).isNull();
        }

        @Test
        @DisplayName("clientKey 可以为空，此时不做幂等")
        void nullClientKeySkipsIdempotency() {
            Long programId = twoDayProgram(List.of(week(1, "0")));

            SessionDetailResponse first = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, null, null));
            sessionService.finish(USER, first.id(), 3600, null);

            // 没有 key，两场训练是两条记录——这是预期的，
            // 手动补录本来就不需要幂等
            SessionDetailResponse second = sessionService.create(USER,
                    new SessionCreateRequest(programId, 1, DAY, null, null));

            assertThat(second.id()).isNotEqualTo(first.id());
        }
    }

    // ==================================================================
    // 测试数据
    // ==================================================================

    /**
     * 只数**本测试**创建的会话。
     *
     * <p>⚠️ 不能写 {@code selectCount(new LambdaQueryWrapper<>())} 数整张表——
     * 那样断言就依赖「库里没有别的会话」这个前提。
     *
     * <p>这个坑真的踩到了：跑完端到端 HTTP 验证之后，
     * 那几条真实会话留在库里，这三个测试立刻变红
     * （`expected: 1L but was: 7L`），而代码一行没改。
     *
     * <p>**数全表的测试是环境依赖的测试。** 按 userId + clientKey 限定，
     * 断言才只关于它自己造的数据。
     */
    private long countMySessions(String... clientKeys) {
        return sessionMapper.selectCount(new LambdaQueryWrapper<WorkoutSession>()
                .eq(WorkoutSession::getUserId, USER)
                .in(WorkoutSession::getClientKey, List.of(clientKeys)));
    }

    /** 两天的计划（推日 / 腿日），不限期 */
    private Long twoDayProgram(List<ProgramCreateRequest.WeekRequest> weeks) {
        return programService.create(USER, new ProgramCreateRequest(
                "轮转验证计划", null, 0, START, weeks,
                List.of(day(1, "推日", prescription(benchId, 3, 8, 10, 150, "60")),
                        day(2, "腿日", prescription(squatId, 5, 5, 5, 180, "100")))));
    }

    private static ProgramCreateRequest.WeekRequest week(int number, String adjust) {
        return week(number, adjust, 0, false);
    }

    private static ProgramCreateRequest.WeekRequest week(
            int number, String adjust, int setAdjust, boolean deload) {
        return new ProgramCreateRequest.WeekRequest(
                number, 3, new BigDecimal(adjust), setAdjust, deload, null);
    }

    private static ProgramCreateRequest.DayRequest day(
            int number, String name, ProgramCreateRequest.PrescriptionRequest... exercises) {
        return new ProgramCreateRequest.DayRequest(number, name, false, null, List.of(exercises));
    }

    private static ProgramCreateRequest.PrescriptionRequest prescription(
            Long exerciseId, int sets, int repsMin, int repsMax, int restSec, String weight) {
        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId, 1, null, null, sets, repsMin, repsMax, restSec,
                TargetWeightType.ABSOLUTE, new BigDecimal(weight),
                null, null, null, null, null, null);
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
