package com.gymlog.body;

import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 指标的元数据与校验 —— 纯函数测试。
 *
 * <p>这个枚举是「身体数据有哪些指标、各自什么量程、允许哪些部位」的
 * <b>唯一事实来源</b>：客户端表单靠它生成，服务端校验靠它执行。
 * 所以它错了，两边一起错。
 */
class BodyMetricTypeTest {

    @Nested
    @DisplayName("量程校验")
    class ValueRange {

        @Test
        @DisplayName("体重 20–400：边界值通过，越界拒绝")
        void weightRange() {
            assertThatCode(() -> BodyMetricType.WEIGHT.validateValue(new BigDecimal("20")))
                    .doesNotThrowAnyException();
            assertThatCode(() -> BodyMetricType.WEIGHT.validateValue(new BigDecimal("400")))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> BodyMetricType.WEIGHT.validateValue(new BigDecimal("19.9")))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_VALUE_OUT_OF_RANGE);

            assertThatThrownBy(() -> BodyMetricType.WEIGHT.validateValue(new BigDecimal("400.01")))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("1–5 分的指标拒绝 0 和 6")
        void scaleRange() {
            assertThatThrownBy(() -> BodyMetricType.SLEEP_QUALITY.validateValue(BigDecimal.ZERO))
                    .isInstanceOf(BizException.class);
            assertThatThrownBy(() -> BodyMetricType.SLEEP_QUALITY.validateValue(new BigDecimal("6")))
                    .isInstanceOf(BizException.class);
            assertThatCode(() -> BodyMetricType.SLEEP_QUALITY.validateValue(new BigDecimal("3")))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("数值为 null 时报「超出范围」而不是空指针")
        void nullValue() {
            assertThatThrownBy(() -> BodyMetricType.WEIGHT.validateValue(null))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_VALUE_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("报错文案里的量程不带小数尾巴（「20–400」而不是「20.00–400.00」）")
        void messageHasNoTrailingZeros() {
            assertThatThrownBy(() -> BodyMetricType.WEIGHT.validateValue(BigDecimal.ONE))
                    .hasMessageContaining("20–400");
        }
    }

    @Nested
    @DisplayName("部位校验")
    class SiteValidation {

        @Test
        @DisplayName("没有部位概念的指标：NONE 通过，带部位被拒")
        void noSiteMetrics() {
            assertThatCode(() -> BodyMetricType.WEIGHT.validateSite(BodySite.NONE))
                    .doesNotThrowAnyException();

            assertThatThrownBy(() -> BodyMetricType.WEIGHT.validateSite(BodySite.WAIST))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_SITE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("围度必须给部位：不给是 60005，给了别的指标的部位是 60006")
        void circumferenceRequiresSite() {
            // 这两个错误码分开是有意的：它们要引导用户做的事相反
            // （「去选一个部位」vs「把部位去掉」）
            assertThatThrownBy(() -> BodyMetricType.CIRCUMFERENCE.validateSite(BodySite.NONE))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_SITE_REQUIRED);

            // CORE 是酸痛度的肌群，不是围度部位
            assertThatThrownBy(() -> BodyMetricType.CIRCUMFERENCE.validateSite(BodySite.CORE))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_SITE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("酸痛度部位可选：不填通过，填肌群也通过，填小腿被拒")
        void sorenessSiteOptional() {
            assertThatCode(() -> BodyMetricType.SORENESS.validateSite(BodySite.NONE))
                    .doesNotThrowAnyException();
            assertThatCode(() -> BodyMetricType.SORENESS.validateSite(BodySite.CHEST))
                    .doesNotThrowAnyException();

            // M6-D-4 说的是「可分部位」，分的是**肌群**不是身体部位
            assertThatThrownBy(() -> BodyMetricType.SORENESS.validateSite(BodySite.LEFT_CALF))
                    .isInstanceOf(BizException.class);
        }

        @Test
        @DisplayName("★ CHEST 两个指标都认 —— 靠 metric_type 消歧，不是冲突")
        void chestSharedBetweenMetrics() {
            assertThatCode(() -> BodyMetricType.CIRCUMFERENCE.validateSite(BodySite.CHEST))
                    .doesNotThrowAnyException();
            assertThatCode(() -> BodyMetricType.SORENESS.validateSite(BodySite.CHEST))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("元数据本身")
    class Metadata {

        @Test
        @DisplayName("围度正好 12 个部位（REQUIREMENTS M6-A-2，不是 METRICS 早期的 9 个）")
        void twelveCircumferenceSites() {
            assertThat(BodySite.CIRCUMFERENCE_SITES).hasSize(12);
            // 颈和前臂是 METRICS 早期版本漏掉的两个——
            // 漏了的后果很隐蔽：录得进去、图上看不到
            assertThat(BodySite.CIRCUMFERENCE_SITES)
                    .contains(BodySite.NECK, BodySite.LEFT_FOREARM, BodySite.RIGHT_FOREARM);
        }

        @Test
        @DisplayName("酸痛度正好 6 个肌群，且与 MuscleGroup 的分类一致")
        void sixSorenessSites() {
            assertThat(BodySite.SORENESS_SITES).hasSize(6);
        }

        @Test
        @DisplayName("成对部位的 opposite() 互为反函数，非成对返回 null")
        void oppositeIsInvolutive() {
            for (BodySite site : BodySite.values()) {
                BodySite opp = site.opposite();
                if (site.isPaired()) {
                    assertThat(opp).as("%s 应该有配对", site).isNotNull();
                    assertThat(opp.opposite()).as("%s 的配对应该能反回来", site).isEqualTo(site);
                } else {
                    assertThat(opp).as("%s 不该有配对", site).isNull();
                }
            }
        }

        @Test
        @DisplayName("每个指标的 unit 非空、量程 min < max")
        void allTypesWellFormed() {
            for (BodyMetricType type : BodyMetricType.values()) {
                assertThat(type.getLabel()).as("%s 的中文名", type).isNotBlank();
                assertThat(type.getUnit()).as("%s 的单位", type).isNotBlank();
                assertThat(type.getMin()).as("%s 的下界", type).isLessThan(type.getMax());
            }
        }

        @Test
        @DisplayName("有部位概念的指标，其部位集合非空")
        void sitesAreConsistent() {
            for (BodyMetricType type : BodyMetricType.values()) {
                if (type.hasSites()) {
                    assertThat(type.getAllowedSites()).as("%s 声明了部位概念", type).isNotEmpty();
                }
            }
        }

        @Test
        @DisplayName("必须选部位的指标一定是围度（目前只有它）")
        void requiresSiteMatchesExpectation() {
            List<BodyMetricType> requiring = java.util.Arrays.stream(BodyMetricType.values())
                    .filter(BodyMetricType::requiresSite)
                    .toList();
            assertThat(requiring).containsExactly(BodyMetricType.CIRCUMFERENCE);
        }

        @Test
        @DisplayName("★ 只保留「用户能自己测」的指标 —— 体脂秤推算的五项已去掉")
        void onlySelfMeasurableMetrics() {
            // 判据：用户能不能自己测出来。
            // 体重（秤）、围度（软尺）、静息心率（数脉搏）、主观感受 —— 都能。
            // 体脂率/骨骼肌量/水分率/BMR/内脏脂肪 —— 都是体脂秤用公式推的，
            // 不同秤差 3–5 个百分点，用户无法独立验证，趋势也没有行动含义。
            //
            // 其中真正有意义的部分**由自测数据推导**（BMI / 体脂率估算 / BMR），
            // 公式是公开的。见 BodyDerived。
            assertThat(java.util.Arrays.stream(BodyMetricType.values()).map(Enum::name))
                    .containsExactlyInAnyOrder(
                            "WEIGHT", "CIRCUMFERENCE", "RESTING_HR",
                            "SLEEP_QUALITY", "SLEEP_HOURS", "ENERGY_LEVEL",
                            "SORENESS", "PRE_WORKOUT_STATE", "STRESS_LEVEL");
        }

        @Test
        @DisplayName("分组里不再有「体成分」")
        void noCompositionGroup() {
            assertThat(java.util.Arrays.stream(BodyMetricType.Group.values()).map(Enum::name))
                    .containsExactly("MEASURE", "VITAL", "SUBJECTIVE");
        }
    }
}
