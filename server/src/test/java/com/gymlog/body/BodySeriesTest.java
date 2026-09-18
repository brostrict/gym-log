package com.gymlog.body;

import com.gymlog.stats.MovingAverage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 身体序列的日聚合 —— 纯函数测试，不起 Spring。
 *
 * <p>这里守的是 {@code METRICS 1.2} 的**第一步**，也是整个体重图表最容易写错的地方：
 * 同一天测三次，那天的权重**不能**变成三倍。
 */
class BodySeriesTest {

    private static final LocalDate D1 = LocalDate.of(2026, 9, 1);

    private static BodyMetric weight(LocalDateTime at, String value) {
        BodyMetric m = new BodyMetric();
        m.setMetricType(BodyMetricType.WEIGHT);
        m.setSite(BodySite.NONE);
        m.setMeasuredAt(at);
        m.setValue(new BigDecimal(value));
        return m;
    }

    private static BodyMetric circumference(LocalDateTime at, BodySite site, String value) {
        BodyMetric m = new BodyMetric();
        m.setMetricType(BodyMetricType.CIRCUMFERENCE);
        m.setSite(site);
        m.setMeasuredAt(at);
        m.setValue(new BigDecimal(value));
        return m;
    }

    @Nested
    @DisplayName("按自然日取均值")
    class DailyAverage {

        @Test
        @DisplayName("空输入返回空，不抛异常")
        void empty() {
            assertThat(BodySeries.dailyAverage(List.of())).isEmpty();
            assertThat(BodySeries.dailyAverage(null)).isEmpty();
        }

        @Test
        @DisplayName("一天一次测量：值原样保留")
        void onePerDay() {
            var result = BodySeries.dailyAverage(List.of(
                    weight(D1.atTime(7, 0), "72.4"),
                    weight(D1.plusDays(1).atTime(7, 10), "72.1")));

            assertThat(result).hasSize(2);
            assertThat(result.get(0).date()).isEqualTo(D1);
            assertThat(result.get(0).value()).isEqualByComparingTo("72.40");
            assertThat(result.get(1).value()).isEqualByComparingTo("72.10");
        }

        @Test
        @DisplayName("★ 一天测三次 → 合并成一个点，而不是三个点")
        void threeInOneDayCollapseToOne() {
            // 这就是 METRICS 1.2 两步式的全部意义。
            // 如果不合并，「同一天多测几次」会让那天的权重变大，
            // 而 1.3 整节论证的是「压掉日内噪声」——越勤快测的人曲线越抖，
            // 与目的直接矛盾。
            var result = BodySeries.dailyAverage(List.of(
                    weight(D1.atTime(7, 0), "72.0"),
                    weight(D1.atTime(12, 0), "73.0"),
                    weight(D1.atTime(21, 0), "71.0")));

            assertThat(result).hasSize(1);
            assertThat(result.get(0).date()).isEqualTo(D1);
            assertThat(result.get(0).value()).isEqualByComparingTo("72.00");
        }

        @Test
        @DisplayName("均值的除法是 HALF_UP，不是截断")
        void averageRoundsHalfUp() {
            // (72.0 + 72.1 + 72.2) / 3 = 72.1
            var exact = BodySeries.dailyAverage(List.of(
                    weight(D1.atTime(7, 0), "72.0"),
                    weight(D1.atTime(12, 0), "72.1"),
                    weight(D1.atTime(21, 0), "72.2")));
            assertThat(exact.get(0).value()).isEqualByComparingTo("72.10");

            // (70.0 + 70.1) / 2 = 70.05 → 保留两位正好
            var half = BodySeries.dailyAverage(List.of(
                    weight(D1.atTime(7, 0), "70.0"),
                    weight(D1.atTime(21, 0), "70.1")));
            assertThat(half.get(0).value()).isEqualByComparingTo("70.05");

            // 无限小数：100/3 = 33.333... → HALF_UP 到 33.33
            var repeating = BodySeries.dailyAverage(List.of(
                    weight(D1.atTime(7, 0), "100"),
                    weight(D1.atTime(8, 0), "0"),
                    weight(D1.atTime(9, 0), "0")));
            assertThat(repeating.get(0).value()).isEqualByComparingTo("33.33");
        }

        @Test
        @DisplayName("结果按日期升序，且与输入顺序无关")
        void sortedAscendingRegardlessOfInputOrder() {
            // HashMap 的话这里会随机——JSON 里日期顺序一乱，客户端 X 轴就成锯齿
            var result = BodySeries.dailyAverage(List.of(
                    weight(D1.plusDays(2).atTime(7, 0), "71.0"),
                    weight(D1.atTime(7, 0), "72.0"),
                    weight(D1.plusDays(1).atTime(7, 0), "72.5")));

            assertThat(result).extracting(MovingAverage.DailyPoint::date)
                    .containsExactly(D1, D1.plusDays(1), D1.plusDays(2));
        }

        @Test
        @DisplayName("同一天的判定按自然日，不是按 24 小时")
        void dayBoundaryIsCalendarDay() {
            // 23:59 和次日 00:01 只差两分钟，但属于两个自然日。
            // 用「与上一点相差不到 24 小时」来分组的实现会在这里挂掉。
            var result = BodySeries.dailyAverage(List.of(
                    weight(D1.atTime(23, 59), "72.0"),
                    weight(D1.plusDays(1).atTime(0, 1), "73.0")));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("跨月跨年也按自然日分组")
        void crossMonthAndYear() {
            var dec31 = LocalDate.of(2026, 12, 31);
            var jan1 = LocalDate.of(2027, 1, 1);
            var result = BodySeries.dailyAverage(List.of(
                    weight(dec31.atTime(22, 0), "80.0"),
                    weight(jan1.atTime(7, 0), "79.5")));

            assertThat(result).extracting(MovingAverage.DailyPoint::date)
                    .containsExactly(dec31, jan1);
        }
    }

    @Nested
    @DisplayName("原始点")
    class RawPoints {

        @Test
        @DisplayName("带出测量条件，缺失时为 null")
        void carriesCondition() {
            BodyMetric withCondition = weight(D1.atTime(7, 0), "72.4");
            withCondition.setMeasureCondition(MetricCondition.FASTED);

            BodyMetric without = weight(D1.plusDays(1).atTime(7, 0), "72.0");

            var points = BodySeries.toRawPoints(List.of(withCondition, without));

            assertThat(points.get(0).condition()).isEqualTo(MetricCondition.FASTED);
            // 「没填」不是「填了其他」——两者在图上要能区分
            assertThat(points.get(1).condition()).isNull();
        }

        @Test
        @DisplayName("空输入返回空")
        void empty() {
            assertThat(BodySeries.toRawPoints(List.of())).isEmpty();
            assertThat(BodySeries.toRawPoints(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("最新值与变化量")
    class Latest {

        @Test
        @DisplayName("没有数据时返回 null，不是 0")
        void nullWhenEmpty() {
            // 返回 ZERO 的话，「没测过」会被显示成「变化 0」——两件事
            assertThat(BodySeries.latestValue(List.of())).isNull();
            assertThat(BodySeries.latestValue(null)).isNull();
        }

        @Test
        @DisplayName("取列表最后一个（调用方已按时间升序）")
        void lastIsLatest() {
            var result = BodySeries.latestValue(List.of(
                    weight(D1.atTime(7, 0), "72.0"),
                    weight(D1.plusDays(1).atTime(7, 0), "71.5")));

            assertThat(result).isEqualByComparingTo("71.50");
        }
    }

    @Nested
    @DisplayName("有数据的部位")
    class UsedSites {

        @Test
        @DisplayName("按解剖学顺序返回，不是按数据出现顺序")
        void orderedByDeclarationNotAppearance() {
            // 用户如果只测过小腿和颈，切换器也应该是「颈、左小腿」而不是反过来——
            // 按出现顺序的话，用户前几次只测了腰围，腰围就永远排第一
            var result = BodySeries.usedSites(
                    List.of("LEFT_CALF", "NECK", "WAIST"),
                    List.copyOf(BodySite.CIRCUMFERENCE_SITES));

            assertThat(result).containsExactly(BodySite.NECK, BodySite.WAIST, BodySite.LEFT_CALF);
        }

        @Test
        @DisplayName("不在白名单里的部位被丢掉，不会凭空出现")
        void unknownCodesDropped() {
            // 参数是「12 个部位」这个有序全集，不在里面的（比如 SORENESS 的肌群）
            // 不该出现在围度切换器里
            var result = BodySeries.usedSites(
                    List.of("WAIST", "CORE", "NECK"),
                    List.copyOf(BodySite.CIRCUMFERENCE_SITES));

            assertThat(result).containsExactly(BodySite.NECK, BodySite.WAIST);
        }

        @Test
        @DisplayName("重复的部位只出现一次")
        void deduplicated() {
            var result = BodySeries.usedSites(
                    List.of("WAIST", "WAIST"),
                    List.copyOf(BodySite.CIRCUMFERENCE_SITES));

            assertThat(result).containsExactly(BodySite.WAIST);
        }

        @Test
        @DisplayName("空输入返回空")
        void empty() {
            assertThat(BodySeries.usedSites(List.of(), List.of(BodySite.WAIST))).isEmpty();
            assertThat(BodySeries.usedSites(null, List.of(BodySite.WAIST))).isEmpty();
        }
    }
}
