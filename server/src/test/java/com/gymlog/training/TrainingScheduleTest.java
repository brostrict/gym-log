package com.gymlog.training;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「今天是第几周、该练第几天」的单元测试。
 *
 * <p>没有 {@code @SpringBootTest}——纯函数，整个类跑完不到 50 毫秒。
 *
 * <p>这个测试的重点全在**边界**上：
 * 周次差一周意味着用错一整周的重量（第 4 周 +2.5% 和第 8 周 deload -40%
 * 差了一倍多），而这种错误不会报任何异常，只会让用户练错。
 */
class TrainingScheduleTest {

    private static final LocalDate START = LocalDate.of(2026, 9, 21);

    /** 8 周计划，3 个训练日，一次没练过 */
    private static TrainingSchedule.Resolution resolve(LocalDate today) {
        return TrainingSchedule.resolve(START, 8, today, 3, 0);
    }

    // ==================================================================
    // 一、周次
    // ==================================================================

    @Nested
    @DisplayName("周次计算")
    class Weeks {

        @Test
        @DisplayName("开始当天是第 1 周第 0 天")
        void startDateIsWeekOne() {
            TrainingSchedule.Resolution r = resolve(START);
            assertThat(r.weekNumber()).isEqualTo(1);
            assertThat(r.state()).isEqualTo(ScheduleState.ONGOING);
            assertThat(r.daysUntilStart()).isNull();
        }

        @Test
        @DisplayName("第 6 天仍是第 1 周，第 7 天才进第 2 周")
        void weekBoundary() {
            // ⚠️ 这就是最容易差一的地方。
            // 写成 (days / 7) + 1 是对的；写成 ceil(days / 7.0) 会让
            // 第 7 天仍然算第 1 周，累计下来整个计划偏移一周。
            assertThat(resolve(START.plusDays(6)).weekNumber())
                    .as("第 6 天：第 1 周").isEqualTo(1);

            assertThat(resolve(START.plusDays(7)).weekNumber())
                    .as("第 7 天：第 2 周").isEqualTo(2);

            assertThat(resolve(START.plusDays(13)).weekNumber()).isEqualTo(2);
            assertThat(resolve(START.plusDays(14)).weekNumber()).isEqualTo(3);
        }

        @Test
        @DisplayName("最后一天仍在计划内，超出才结束")
        void finishesAfterLastDay() {
            // 8 周 = 56 天。第 56 天（下标 55）是最后一天
            TrainingSchedule.Resolution lastDay = resolve(START.plusDays(55));
            assertThat(lastDay.weekNumber()).isEqualTo(8);
            assertThat(lastDay.state()).isEqualTo(ScheduleState.ONGOING);

            TrainingSchedule.Resolution dayAfter = resolve(START.plusDays(56));
            assertThat(dayAfter.state()).isEqualTo(ScheduleState.FINISHED);
        }

        @Test
        @DisplayName("超期后周次夹在总周数上，不继续增长")
        void clampsWeekNumberWhenFinished() {
            // 三年后再看这个 8 周计划
            TrainingSchedule.Resolution r = resolve(START.plusYears(3));

            assertThat(r.state()).isEqualTo(ScheduleState.FINISHED);
            // ⚠️ 关键：必须夹住。
            // 不夹的话会去查「第 156 周的强度修饰」——查不到，
            // 于是静默按基准值展开，用户看到的是「计划还在继续」。
            assertThat(r.weekNumber()).isEqualTo(8);
        }

        @Test
        @DisplayName("不限期计划永远不会结束")
        void openEndedNeverFinishes() {
            TrainingSchedule.Resolution r =
                    TrainingSchedule.resolve(START, 0, START.plusYears(3), 3, 200);

            assertThat(r.state()).isEqualTo(ScheduleState.ONGOING);
            assertThat(r.totalWeeks()).isZero();
            // 不限期计划的周次是真实增长的——它真的已经练到第 157 周了
            assertThat(r.weekNumber()).isEqualTo(157);
        }

        @Test
        @DisplayName("totalWeeks 为 null 等同于不限期")
        void nullTotalWeeksIsOpenEnded() {
            TrainingSchedule.Resolution r =
                    TrainingSchedule.resolve(START, null, START.plusDays(100), 3, 0);
            assertThat(r.state()).isEqualTo(ScheduleState.ONGOING);
            assertThat(r.totalWeeks()).isZero();
        }
    }

    // ==================================================================
    // 二、还没开始
    // ==================================================================

    @Nested
    @DisplayName("还没开始")
    class NotStarted {

        @Test
        @DisplayName("开始日期在未来时状态是 NOT_STARTED，并给出还有几天")
        void reportsDaysUntilStart() {
            TrainingSchedule.Resolution r = resolve(START.minusDays(3));

            assertThat(r.state()).isEqualTo(ScheduleState.NOT_STARTED);
            assertThat(r.weekNumber()).isEqualTo(1);
            assertThat(r.daysUntilStart()).isEqualTo(3);
        }

        @Test
        @DisplayName("没填开始日期的计划永远停在第 1 周，且不报错")
        void nullStartDateStaysWeekOne() {
            // 「每周三练、不填日期」是合法用法——长期维持的安排
            // 本来就没有「第几周」的概念。这里报错会挡掉一批真实用户。
            TrainingSchedule.Resolution r =
                    TrainingSchedule.resolve(null, 8, LocalDate.of(2026, 9, 21), 3, 5);

            assertThat(r.state()).isEqualTo(ScheduleState.ONGOING);
            assertThat(r.weekNumber()).isEqualTo(1);
            assertThat(r.daysUntilStart()).isNull();
        }
    }

    // ==================================================================
    // 三、训练日轮转
    // ==================================================================

    @Nested
    @DisplayName("训练日轮转：按练了几次推进")
    class Rotation {

        @Test
        @DisplayName("一次没练过时是第 1 个训练日")
        void startsAtFirstDay() {
            assertThat(TrainingSchedule.resolve(START, 8, START, 3, 0).nextDayIndex())
                    .isZero();
        }

        @Test
        @DisplayName("每完成一次推进一个训练日，并循环回开头")
        void advancesAndWraps() {
            // 3 个训练日的计划
            assertThat(idx(3, 0)).isEqualTo(0);
            assertThat(idx(3, 1)).isEqualTo(1);
            assertThat(idx(3, 2)).isEqualTo(2);
            assertThat(idx(3, 3)).as("第 4 次回到第 1 个训练日").isEqualTo(0);
            assertThat(idx(3, 4)).isEqualTo(1);
        }

        @Test
        @DisplayName("2 个训练日 + 每周 3 练：跨周时自动交替（A/B/A → B/A/B）")
        void twoDayPlanAlternatesAcrossWeeks() {
            // 这条断言直接对应 V9 种子文件头部说明的那条规则。
            //
            // 如果是「每周重置」，第 2 周会重新从 A 开始，
            // 变成 A/B/A → A/B/A 无限重复，5×5 的 A/B 交替就废了。
            //
            //   第 1 周：第1次→0(A)  第2次→1(B)  第3次→2→0(A)
            //   第 2 周：第4次→1(B)  第5次→2→0(A)  第6次→1(B)
            assertThat(idx(2, 0)).as("第 1 周 第 1 次 -> A").isZero();
            assertThat(idx(2, 1)).as("第 1 周 第 2 次 -> B").isEqualTo(1);
            assertThat(idx(2, 2)).as("第 1 周 第 3 次 -> A").isZero();

            assertThat(idx(2, 3)).as("第 2 周 第 1 次 -> B").isEqualTo(1);
            assertThat(idx(2, 4)).as("第 2 周 第 2 次 -> A").isZero();
            assertThat(idx(2, 5)).as("第 2 周 第 3 次 -> B").isEqualTo(1);
        }

        @Test
        @DisplayName("轮转不看日期——同一天算多少次结果都一样")
        void rotationIgnoresDate() {
            LocalDate d = START.plusDays(30);
            assertThat(TrainingSchedule.resolve(START, 8, d, 3, 4).nextDayIndex())
                    .isEqualTo(TrainingSchedule.resolve(START, 8, d.plusDays(1), 3, 4).nextDayIndex());
        }

        @Test
        @DisplayName("没有训练日时返回 null，而不是假装有第 0 个")
        void nullWhenNoTrainingDays() {
            TrainingSchedule.Resolution r =
                    TrainingSchedule.resolve(START, 8, START, 0, 0);
            assertThat(r.nextDayIndex()).isNull();
            assertThat(r.trainingDayCount()).isZero();
        }

        @Test
        @DisplayName("已完成的次数原样带回，供客户端显示进度")
        void carriesCompletedCount() {
            assertThat(TrainingSchedule.resolve(START, 8, START, 3, 27).completedSessions())
                    .isEqualTo(27);
        }

        private Integer idx(int dayCount, int completed) {
            return TrainingSchedule.resolve(START, 8, START, dayCount, completed).nextDayIndex();
        }
    }
}
