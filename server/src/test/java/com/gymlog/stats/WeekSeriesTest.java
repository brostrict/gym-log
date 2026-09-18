package com.gymlog.stats;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 周粒度工具。
 *
 * <p>没有 {@code @SpringBootTest}——纯函数，整个类跑完不到 50 毫秒。
 * 也没有 {@code LocalDate.now()}：所有日期都由测试显式给出，
 * 否则「当前周」的判定会随跑测试的时间漂移。
 */
class WeekSeriesTest {

    /** 2026-09-18 是周五 */
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 18);
    /** 同一周的周一 */
    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 14);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 9, 20);

    @Nested
    @DisplayName("周起点必须是周一（METRICS 0.3：国内用户习惯）")
    class WeekStart {

        @Test
        @DisplayName("周中任意一天都归到本周一")
        void anyDayMapsToMonday() {
            assertThat(WeekSeries.weekStart(MONDAY)).isEqualTo(MONDAY);
            assertThat(WeekSeries.weekStart(FRIDAY)).isEqualTo(MONDAY);
        }

        @Test
        @DisplayName("★ 周日归到**本周**一，不是下周一")
        void sundayBelongsToTheWeekThatStartedBefore() {
            // 这条最容易写错。如果按「周日是一周之始」的西方习惯，
            // 2026-09-20（周日）会被算成 09-21（下周一）——整张图的 X 轴错位一格，
            // 而且没人会发现。
            assertThat(WeekSeries.weekStart(SUNDAY)).isEqualTo(MONDAY);
        }

        @Test
        @DisplayName("跨年也对")
        void acrossYearBoundary() {
            // 2027-01-01 是周五，本周一是 2026-12-28
            assertThat(WeekSeries.weekStart(LocalDate.of(2027, 1, 1)))
                    .isEqualTo(LocalDate.of(2026, 12, 28));
        }
    }

    @Nested
    @DisplayName("枚举区间覆盖的每一周")
    class WeekStarts {

        @Test
        @DisplayName("含首尾，升序，步长 7 天")
        void inclusiveAndAscending() {
            List<LocalDate> weeks = WeekSeries.weekStarts(MONDAY, MONDAY.plusWeeks(3));
            assertThat(weeks).containsExactly(
                    MONDAY, MONDAY.plusWeeks(1), MONDAY.plusWeeks(2), MONDAY.plusWeeks(3));
        }

        @Test
        @DisplayName("两端在周中间也能正确归位")
        void midWeekBounds() {
            // 从周三到次周二 → 覆盖两个周一
            List<LocalDate> weeks =
                    WeekSeries.weekStarts(LocalDate.of(2026, 9, 16), LocalDate.of(2026, 9, 22));
            assertThat(weeks).containsExactly(MONDAY, MONDAY.plusWeeks(1));
        }

        @Test
        @DisplayName("同周内 → 只有一周")
        void sameWeek() {
            assertThat(WeekSeries.weekStarts(MONDAY, SUNDAY)).containsExactly(MONDAY);
        }

        @Test
        @DisplayName("★ 空周不会被跳过（METRICS 4.6：空周显示 0 高度柱）")
        void emptyWeeksAreKept() {
            // 枚举是**按日历铺开**的，与有没有训练无关。
            // 如果按「查出有数据的周」来，中断三周会被画成连续三周——
            // 正好掩盖了最该看见的东西。
            List<LocalDate> weeks = WeekSeries.weekStarts(MONDAY, MONDAY.plusWeeks(6));
            assertThat(weeks).hasSize(7);
        }

        @Test
        @DisplayName("from > to → 空列表，不抛异常")
        void reversedRange() {
            assertThat(WeekSeries.weekStarts(MONDAY, MONDAY.minusWeeks(1))).isEmpty();
        }
    }

    @Nested
    @DisplayName("连续训练周数 streak")
    class Streak {

        private Map<LocalDate, Integer> counts(Object... weekAndCount) {
            Map<LocalDate, Integer> map = new HashMap<>();
            for (int i = 0; i < weekAndCount.length; i += 2) {
                map.put((LocalDate) weekAndCount[i], (Integer) weekAndCount[i + 1]);
            }
            return map;
        }

        @Test
        @DisplayName("★ 当前周没练也不算断（METRICS 5.5 的理由：避免周一就显示断连）")
        void currentWeekNotYetTrainedDoesNotBreak() {
            // 过去 3 周都练了，本周（周五）还没练
            Map<LocalDate, Integer> m = counts(
                    MONDAY.minusWeeks(1), 3,
                    MONDAY.minusWeeks(2), 3,
                    MONDAY.minusWeeks(3), 3);
            assertThat(WeekSeries.currentStreak(m, FRIDAY)).isEqualTo(3);
        }

        @Test
        @DisplayName("当前周练了就计入（对 5.5 的解释：按它自己写的理由实现）")
        void currentWeekCountsWhenTrained() {
            Map<LocalDate, Integer> m = counts(
                    MONDAY, 2,
                    MONDAY.minusWeeks(1), 3,
                    MONDAY.minusWeeks(2), 3);
            assertThat(WeekSeries.currentStreak(m, FRIDAY)).isEqualTo(3);
        }

        @Test
        @DisplayName("上周断了 → 只算本周这一段（METRICS 5.2「从当前周往前数」）")
        void gapLastWeekRestartsStreak() {
            // 本周练了 3 次，上周没练。
            // 「从当前周往前，连续满足的周数」= 1 —— 刚才这一段就是从本周开始的。
            // （不是 0：「连续周数」问的是当前这一段有多长，不是「历史上有没有断过」。）
            Map<LocalDate, Integer> m = counts(MONDAY, 3);
            assertThat(WeekSeries.currentStreak(m, FRIDAY)).isEqualTo(1);
        }

        @Test
        @DisplayName("本周和上周都没练 → 0")
        void twoEmptyWeeks() {
            Map<LocalDate, Integer> m = counts(MONDAY.minusWeeks(2), 3);
            assertThat(WeekSeries.currentStreak(m, FRIDAY)).isZero();
        }

        @Test
        @DisplayName("中间断一周，只数到断点为止")
        void stopsAtFirstGap() {
            Map<LocalDate, Integer> m = counts(
                    MONDAY.minusWeeks(1), 3,
                    MONDAY.minusWeeks(2), 3,
                    // 第 3 周缺席
                    MONDAY.minusWeeks(4), 3,
                    MONDAY.minusWeeks(5), 3);
            assertThat(WeekSeries.currentStreak(m, FRIDAY)).isEqualTo(2);
        }

        @Test
        @DisplayName("完全没有记录 → 0，不抛异常")
        void noData() {
            assertThat(WeekSeries.currentStreak(Map.of(), FRIDAY)).isZero();
        }
    }

    @Test
    @DisplayName("当前周判定（用于半透明显示）")
    void currentWeekFlag() {
        assertThat(WeekSeries.isCurrentWeek(MONDAY, FRIDAY)).isTrue();
        assertThat(WeekSeries.isCurrentWeek(MONDAY.minusWeeks(1), FRIDAY)).isFalse();
    }
}
