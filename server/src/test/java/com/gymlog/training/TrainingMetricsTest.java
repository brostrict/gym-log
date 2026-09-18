package com.gymlog.training;

import com.gymlog.exercise.MetricType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 容量与 e1RM 的口径。纯函数，无 Spring。
 *
 * <p>这个类里的断言直接对应 METRICS.md 的条款和验收标准编号——
 * 需求改了，这里就该红。
 */
class TrainingMetricsTest {

    // ==================================================================
    // 一、容量
    // ==================================================================

    @Nested
    @DisplayName("容量口径（METRICS 4.1）")
    class Volume {

        @Test
        @DisplayName("负重动作：重量 × 次数")
        void weightReps() {
            assertThat(volume(MetricType.WEIGHT_REPS, SetType.WORKING, "80", 8, null, null))
                    .isEqualByComparingTo("640");
        }

        @Test
        @DisplayName("⭐ 热身组不计入容量（AC-7-1）")
        void warmupExcluded() {
            assertThat(volume(MetricType.WEIGHT_REPS, SetType.WARMUP, "80", 8, null, null))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("⭐ 自重动作：体重 × bw_factor × 次数（AC-7-8）")
        void bodyweight() {
            // 80kg 的人做引体，bw_factor = 1.00，做 10 次 → 800
            assertThat(volume(MetricType.REPS_ONLY, SetType.WORKING, null, 10,
                    new BigDecimal("80"), new BigDecimal("1.00")))
                    .isEqualByComparingTo("800");
        }

        @Test
        @DisplayName("bw_factor 取自动作快照，不是硬编码的 1.0（AC-7-8）")
        void bwFactorComesFromSnapshot() {
            // 俯卧撑的系数不是 1.00（只有一部分体重压在手上）
            BigDecimal pushup = volume(MetricType.REPS_ONLY, SetType.WORKING, null, 10,
                    new BigDecimal("80"), new BigDecimal("0.65"));

            assertThat(pushup)
                    .as("80 × 0.65 × 10 = 520，而不是 800")
                    .isEqualByComparingTo("520");
        }

        @Test
        @DisplayName("体重未记录时自重动作不计入容量，而不是拿假体重算")
        void bodyweightWithoutWeightIsZero() {
            assertThat(volume(MetricType.REPS_ONLY, SetType.WORKING, null, 10, null,
                    new BigDecimal("1.00")))
                    .as("宁可不计，也不能算出一个会被当成真的数字")
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("⭐ 时长类动作不计入容量（AC-7-9）")
        void durationExcluded() {
            assertThat(volume(MetricType.DURATION, SetType.WORKING, null, null, null, null))
                    .isEqualByComparingTo("0");
            assertThat(volume(MetricType.DISTANCE_DURATION, SetType.WORKING, null, null, null, null))
                    .isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("没有次数的组不计入")
        void noRepsIsZero() {
            assertThat(volume(MetricType.WEIGHT_REPS, SetType.WORKING, "80", null, null, null))
                    .isEqualByComparingTo("0");
        }
    }

    // ==================================================================
    // 二、估算 1RM
    // ==================================================================

    @Nested
    @DisplayName("估算 1RM（Epley）")
    class E1rm {

        @Test
        @DisplayName("⭐ AC-7-6 的两个锚点：5kg×12 → 7kg，20kg×3 → 22kg")
        void acceptanceCriteriaAnchors() {
            // 这两个数字来自验收标准。它们同时说明了「为什么不能直接比重量」：
            // 看重量是 20 > 5，看容量是 60 = 60 打平，只有 e1RM 能分出高下。
            assertThat(TrainingMetrics.e1rm(new BigDecimal("5"), 12))
                    .isEqualByComparingTo("7");
            assertThat(TrainingMetrics.e1rm(new BigDecimal("20"), 3))
                    .isEqualByComparingTo("22");
        }

        @Test
        @DisplayName("12 次是边界，仍然参与计算")
        void twelveRepsIncluded() {
            assertThat(TrainingMetrics.e1rm(new BigDecimal("100"), 12))
                    .isEqualByComparingTo("140");
        }

        @Test
        @DisplayName("⭐ 超过 12 次返回 null，不参与（METRICS 3.3 规则 2）")
        void overTwelveExcluded() {
            // Epley 在高次数区间严重高估——20 次力竭组的真实 1RM
            // 远低于公式给出的值。不排除的话曲线会被高次数组虚抬。
            assertThat(TrainingMetrics.e1rm(new BigDecimal("100"), 13)).isNull();
            assertThat(TrainingMetrics.e1rm(new BigDecimal("100"), 20)).isNull();
        }

        @Test
        @DisplayName("返回 null 而不是 0 —— 0 会污染 MAX")
        void returnsNullNotZero() {
            assertThat(TrainingMetrics.e1rm(null, 5)).isNull();
            assertThat(TrainingMetrics.e1rm(new BigDecimal("100"), null)).isNull();
            assertThat(TrainingMetrics.e1rm(new BigDecimal("100"), 0)).isNull();
        }

        @Test
        @DisplayName("只有 weight_reps 动作有 e1RM（METRICS 3.6）")
        void onlyWeightRepsHasE1rm() {
            assertThat(TrainingMetrics.bestE1rm(
                    record(SetType.WORKING, "80", 8),
                    exercise(MetricType.WEIGHT_REPS))).isEqualByComparingTo("101.33");

            assertThat(TrainingMetrics.bestE1rm(
                    record(SetType.WORKING, null, 10),
                    exercise(MetricType.REPS_ONLY)))
                    .as("引体向上没有 1RM 的概念")
                    .isNull();
        }

        @Test
        @DisplayName("热身组不参与 e1RM")
        void warmupExcludedFromE1rm() {
            assertThat(TrainingMetrics.bestE1rm(
                    record(SetType.WARMUP, "80", 8),
                    exercise(MetricType.WEIGHT_REPS))).isNull();
        }
    }

    // ==================================================================
    // 脚手架
    // ==================================================================

    private static BigDecimal volume(MetricType metric, SetType setType,
                                     String weight, Integer reps,
                                     BigDecimal bodyWeight, BigDecimal bwFactor) {
        SetRecord r = record(setType, weight, reps);
        SessionExercise e = exercise(metric);
        e.setBwFactor(bwFactor);

        WorkoutSession s = new WorkoutSession();
        s.setBodyWeightKg(bodyWeight);

        return TrainingMetrics.setVolume(r, e, s);
    }

    private static SetRecord record(SetType type, String weight, Integer reps) {
        SetRecord r = new SetRecord();
        r.setSetType(type);
        r.setWeight(weight == null ? null : new BigDecimal(weight));
        r.setReps(reps);
        return r;
    }

    private static SessionExercise exercise(MetricType metric) {
        SessionExercise e = new SessionExercise();
        e.setMetricType(metric);
        return e;
    }
}
