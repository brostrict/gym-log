package com.gymlog.stats;

import com.gymlog.stats.MovingAverage.DailyPoint;
import com.gymlog.stats.MovingAverage.MaPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 日历窗口移动平均。纯函数，无 Spring。 */
class MovingAverageTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);

    private static DailyPoint p(int dayOffset, String value) {
        return new DailyPoint(D1.plusDays(dayOffset), new BigDecimal(value));
    }

    @Nested
    @DisplayName("日历窗口 —— 不是「最近 N 个点」")
    class CalendarWindow {

        @Test
        @DisplayName("★ 窗口按日期算：跨 3 周的两个点不该进同一个 7 日窗口")
        void windowIsCalendarBasedNotCountBased() {
            // 两个点相隔 20 天。若按「最近 2 个点」算，它们会互相平均；
            // 按日历窗口算，各自窗口里只有自己 → MA 都不可用。
            List<MaPoint> r = MovingAverage.rolling(
                    List.of(p(0, "70"), p(20, "72")), 7);

            assertThat(r).hasSize(2);
            assertThat(r.get(0).ma()).as("窗口内只有 1 个点，MA 不可用").isNull();
            assertThat(r.get(1).ma()).as("同上").isNull();
        }

        @Test
        @DisplayName("相隔 6 天（含首尾共 7 天）→ 在同一个窗口里")
        void sixDaysApartIsWithinWindow() {
            List<MaPoint> r = MovingAverage.rolling(
                    List.of(p(0, "70"), p(6, "72")), 7);

            // p(6) 的窗口是 [p(0), p(6)]，两个点 → MA = 71
            assertThat(r.get(1).ma()).isEqualByComparingTo("71.00");
            // p(0) 的窗口是 [D1-6, D1]，只有自己 → 不可用
            assertThat(r.get(0).ma()).isNull();
        }

        @Test
        @DisplayName("相隔 7 天 → 刚好出窗口")
        void sevenDaysApartIsOutside() {
            List<MaPoint> r = MovingAverage.rolling(
                    List.of(p(0, "70"), p(7, "72")), 7);
            assertThat(r.get(1).ma()).isNull();
        }

        @Test
        @DisplayName("窗口滑动时旧点被移出")
        void slidesOut() {
            List<DailyPoint> points = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                points.add(p(i, String.valueOf(70 + i)));
            }
            List<MaPoint> r = MovingAverage.rolling(points, 3);

            // 第 3 天（index 2）窗口 = [0,1,2] → (70+71+72)/3 = 71
            assertThat(r.get(2).ma()).isEqualByComparingTo("71.00");
            // 第 4 天，70 已滑出 → (71+72+73)/3 = 72
            assertThat(r.get(3).ma()).isEqualByComparingTo("72.00");
            // 最后一天 → (77+78+79)/3 = 78
            assertThat(r.get(9).ma()).isEqualByComparingTo("78.00");
        }
    }

    @Nested
    @DisplayName("数据不足")
    class NotEnoughData {

        @Test
        @DisplayName("整条序列 < 3 个点 → enoughDataForMa 为 false（AC-7-2）")
        void fewerThanThreePoints() {
            assertThat(MovingAverage.enoughDataForMa(List.of(p(0, "70")))).isFalse();
            assertThat(MovingAverage.enoughDataForMa(List.of(p(0, "70"), p(1, "71")))).isFalse();
            assertThat(MovingAverage.enoughDataForMa(
                    List.of(p(0, "70"), p(1, "71"), p(2, "72")))).isTrue();
        }

        @Test
        @DisplayName("空输入不抛异常")
        void empty() {
            assertThat(MovingAverage.rolling(List.of(), 7)).isEmpty();
            assertThat(MovingAverage.rolling(null, 7)).isEmpty();
            assertThat(MovingAverage.enoughDataForMa(null)).isFalse();
        }

        @Test
        @DisplayName("窗口里只有自己 → MA 是 null 而不是原值")
        void singlePointInWindowIsNullNotSelf() {
            List<MaPoint> r = MovingAverage.rolling(List.of(p(0, "70")), 7);
            // 返回 70.00 的话，图上会出现一条「有移动平均」的线，
            // 而它一点噪声都没压掉——比没有更误导。
            assertThat(r.get(0).ma()).isNull();
        }
    }

    @Nested
    @DisplayName("输入顺序")
    class Ordering {

        @Test
        @DisplayName("★ 乱序输入也要算出正确结果（内部防御性排序）")
        void unsortedInputStillCorrect() {
            List<MaPoint> r = MovingAverage.rolling(
                    List.of(p(2, "72"), p(0, "70"), p(1, "71")), 7);

            assertThat(r).extracting(MaPoint::date).isSorted();
            assertThat(r.get(2).ma()).isEqualByComparingTo("71.00");
        }

        @Test
        @DisplayName("输出按日期升序")
        void outputIsAscending() {
            List<MaPoint> r = MovingAverage.rolling(
                    List.of(p(5, "75"), p(1, "71"), p(3, "73")), 7);
            assertThat(r).extracting(MaPoint::date).isSorted();
        }
    }

    @Test
    @DisplayName("窗口天数非法要报错，不能静默算错")
    void invalidWindow() {
        assertThat(catchThrowable(() -> MovingAverage.rolling(List.of(p(0, "70")), 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Throwable catchThrowable(Runnable r) {
        try {
            r.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
