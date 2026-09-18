package com.gymlog.stats;

import com.gymlog.stats.Adherence.DeviationTag;
import com.gymlog.stats.Adherence.PlannedActual;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 计划执行度。纯函数，无 Spring。 */
class AdherenceTest {

    @Nested
    @DisplayName("符合率")
    class Compliance {

        @Test
        @DisplayName("★ 超额完成也不超过 100%（AC-7-5）")
        void neverExceedsOneHundred() {
            // 计划 3 组，实际做了 5 组
            assertThat(Adherence.complianceRate(List.of(new PlannedActual(3, 5))))
                    .isEqualByComparingTo("100.0");
        }

        @Test
        @DisplayName("★ clamp 是逐单元的，不是先求和再 clamp")
        void clampIsPerUnit() {
            // A 多做 3 组、B 少做 3 组。总量相等，但执行度并不完美。
            // 先求和再 clamp 会得到 100% —— 掩盖了「有一个动作完全没按计划做」。
            var perUnit = Adherence.complianceRate(List.of(
                    new PlannedActual(3, 6),   // clamp 到 3
                    new PlannedActual(6, 3))); // 3

            // 逐单元：(3 + 3) / (3 + 6) = 66.7%
            assertThat(perUnit).isEqualByComparingTo("66.7");
        }

        @Test
        @DisplayName("部分完成")
        void partial() {
            assertThat(Adherence.complianceRate(List.of(
                    new PlannedActual(10, 7), new PlannedActual(10, 8))))
                    .isEqualByComparingTo("75.0");
        }

        @Test
        @DisplayName("★ 计划为 0 时返回 null，不是 0")
        void noPlanReturnsNull() {
            // 「没计划」和「完成度为零」是完全不同的两件事。
            // 返回 0 的话界面上会显示「符合率 0%」——把「没设定目标」
            // 渲染成了「你彻底失败了」。
            assertThat(Adherence.complianceRate(List.of(new PlannedActual(0, 5)))).isNull();
            assertThat(Adherence.complianceRate(List.of())).isNull();
            assertThat(Adherence.complianceRate(null)).isNull();
        }

        @Test
        @DisplayName("计划为 0 的单元被跳过，不拉低整体")
        void zeroPlannedUnitsAreSkipped() {
            assertThat(Adherence.complianceRate(List.of(
                    new PlannedActual(0, 5),
                    new PlannedActual(10, 5))))
                    .isEqualByComparingTo("50.0");
        }
    }

    @Nested
    @DisplayName("偏差标签（METRICS 6.3）")
    class Tags {

        @Test
        @DisplayName("★ 优先级：临时 > 替换 > 修改 > 按计划")
        void priority() {
            List<PlannedActual> adjusted = List.of(new PlannedActual(3, 4));

            // 一场既有替换又有微调的训练，用户最想知道的是「换动作了」
            assertThat(Adherence.classify(false, true, adjusted)).isEqualTo(DeviationTag.SUBSTITUTED);
            // 临时训练盖过一切
            assertThat(Adherence.classify(true, true, adjusted)).isEqualTo(DeviationTag.AD_HOC);
        }

        @Test
        @DisplayName("完全匹配 → 按计划")
        void asPlanned() {
            assertThat(Adherence.classify(false, false, List.of(
                    new PlannedActual(3, 3), new PlannedActual(4, 4))))
                    .isEqualTo(DeviationTag.AS_PLANNED);
        }

        @Test
        @DisplayName("对不上 → 修改")
        void modified() {
            assertThat(Adherence.classify(false, false, List.of(
                    new PlannedActual(3, 3), new PlannedActual(4, 5))))
                    .isEqualTo(DeviationTag.MODIFIED);
        }

        @Test
        @DisplayName("标签有中文名（METRICS 6.4 要求不用红绿二元判断）")
        void hasLabels() {
            assertThat(DeviationTag.AS_PLANNED.getLabel()).isEqualTo("按计划");
            assertThat(DeviationTag.MODIFIED.getLabel()).isEqualTo("修改");
        }
    }
}
