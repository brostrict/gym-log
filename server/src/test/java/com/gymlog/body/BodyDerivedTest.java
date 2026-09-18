package com.gymlog.body;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推导公式 —— 纯函数测试，不起 Spring。
 *
 * <p>这些公式的**系数写错一个数字不会报错**，只会让所有人看到的值都偏一点。
 * 所以每条都用**手算的期望值**钉死，而不是「跑一遍看看」。
 */
class BodyDerivedTest {

    @Nested
    @DisplayName("BMI")
    class Bmi {

        @Test
        @DisplayName("73.3kg / 175cm → 23.9（正好压在正常范围上界）")
        void normal() {
            // 73.3 / 1.75² = 73.3 / 3.0625 = 23.93... → 23.9
            assertThat(BodyDerived.bmi(new BigDecimal("73.3"), new BigDecimal("175")))
                    .isEqualByComparingTo("23.9");
        }

        @Test
        @DisplayName("★ 身高按米算，不是厘米——差 10000 倍")
        void heightIsInMeters() {
            // 忘了 movePointLeft(2) 的话会算出 73.3/175² = 0.0024，
            // 而它作为 BMI 明显荒谬，却不会抛异常
            BigDecimal v = BodyDerived.bmi(new BigDecimal("73.3"), new BigDecimal("175"));
            assertThat(v).isBetween(new BigDecimal("15"), new BigDecimal("35"));
        }

        @Test
        @DisplayName("资料不全返回 null，不算一个假值")
        void nullWhenIncomplete() {
            assertThat(BodyDerived.bmi(null, new BigDecimal("175"))).isNull();
            assertThat(BodyDerived.bmi(new BigDecimal("73.3"), null)).isNull();
            // 身高为 0 会除零
            assertThat(BodyDerived.bmi(new BigDecimal("73.3"), BigDecimal.ZERO)).isNull();
        }

        @Test
        @DisplayName("分级用中国标准（24 超重、28 肥胖），不是 WHO 的 25/30")
        void chineseThresholds() {
            assertThat(BodyDerived.bmiLabel(new BigDecimal("18.4"))).isEqualTo("偏瘦");
            assertThat(BodyDerived.bmiLabel(new BigDecimal("18.5"))).isEqualTo("正常");
            assertThat(BodyDerived.bmiLabel(new BigDecimal("23.9"))).isEqualTo("正常");
            // WHO 标准里 24 还是「正常」，中国标准已经是「超重」——
            // 混用会让一批中国人被告知「你正常」而实际已经超重
            assertThat(BodyDerived.bmiLabel(new BigDecimal("24.0"))).isEqualTo("超重");
            assertThat(BodyDerived.bmiLabel(new BigDecimal("28.0"))).isEqualTo("肥胖");
        }
    }

    @Nested
    @DisplayName("体脂率估算（Deurenberg）")
    class BodyFat {

        @Test
        @DisplayName("★ 性别是 1/0，不是 2/1")
        void sexTermIsOneOrZero() {
            // ⚠️ 这条守的是最容易错的一处：User.GENDER_MALE = 1、GENDER_FEMALE = 2，
            // 直接把 2 代进公式的话，女性会算出**比男性还低**的体脂率。
            // 公式要的是 male ? 1 : 0，所以 BodyDerived 显式映射，不直接用枚举值。
            var male = BodyDerived.bodyFatPercent(new BigDecimal("80"), new BigDecimal("175"),
                    30, true);
            var female = BodyDerived.bodyFatPercent(new BigDecimal("80"), new BigDecimal("175"),
                    30, false);

            assertThat(female).as("同样身高体重年龄，女性体脂率必须更高").isGreaterThan(male);
            // 差值正好是 10.8
            assertThat(female.subtract(male)).isEqualByComparingTo("10.8");
        }

        @Test
        @DisplayName("80kg / 175cm / 30 岁 / 男 → 22.8%")
        void handComputed() {
            // BMI = 80/1.75² = 26.122 → 26.1（保留一位）
            // BF% = 1.20×26.1 + 0.23×30 − 10.8×1 − 5.4
            //     = 31.32 + 6.9 − 10.8 − 5.4 = 22.02 → 22.0
            assertThat(BodyDerived.bodyFatPercent(new BigDecimal("80"), new BigDecimal("175"),
                    30, true)).isEqualByComparingTo("22.0");
        }

        @Test
        @DisplayName("★ 极端输入被夹到 0–100，不返回负数或 150%")
        void clampedToPhysiologicalRange() {
            // 很瘦的年轻人：公式会给出负值
            var skinny = BodyDerived.bodyFatPercent(new BigDecimal("50"), new BigDecimal("190"),
                    18, true);
            assertThat(skinny).isGreaterThanOrEqualTo(BigDecimal.ZERO);

            // 很胖的人：会超过 100
            var heavy = BodyDerived.bodyFatPercent(new BigDecimal("200"), new BigDecimal("160"),
                    60, true);
            assertThat(heavy).isLessThanOrEqualTo(new BigDecimal("100"));
        }

        @Test
        @DisplayName("缺性别或年龄时返回 null")
        void nullWhenIncomplete() {
            assertThat(BodyDerived.bodyFatPercent(new BigDecimal("80"),
                    new BigDecimal("175"), 30, null)).isNull();
            assertThat(BodyDerived.bodyFatPercent(new BigDecimal("80"),
                    new BigDecimal("175"), null, true)).isNull();
        }

        @Test
        @DisplayName("分级阈值男女不同")
        void labelsAreSexSpecific() {
            // 同一个 22%，男性是「偏高」，女性是「健康」
            assertThat(BodyDerived.bodyFatLabel(new BigDecimal("22"), true)).isEqualTo("偏高");
            assertThat(BodyDerived.bodyFatLabel(new BigDecimal("22"), false)).isEqualTo("健康");
        }
    }

    @Nested
    @DisplayName("BMR（Mifflin-St Jeor）")
    class Bmr {

        @Test
        @DisplayName("男：80kg / 175cm / 30 岁 → 1739 kcal")
        void male() {
            // 10×80 + 6.25×175 − 5×30 + 5 = 800 + 1093.75 − 150 + 5 = 1748.75 → 1749
            assertThat(BodyDerived.bmr(new BigDecimal("80"), new BigDecimal("175"), 30, true))
                    .isEqualByComparingTo("1749");
        }

        @Test
        @DisplayName("女：同一个人的常数项是 −161 而不是 +5，差 166")
        void female() {
            assertThat(BodyDerived.bmr(new BigDecimal("80"), new BigDecimal("175"), 30, false))
                    .isEqualByComparingTo("1583");
            assertThat(BodyDerived.bmr(new BigDecimal("80"), new BigDecimal("175"), 30, true)
                    .subtract(BodyDerived.bmr(new BigDecimal("80"), new BigDecimal("175"), 30, false)))
                    .isEqualByComparingTo("166");
        }

        @Test
        @DisplayName("体重越大 BMR 越高（系数 10 是三项里最大的）")
        void scalesWithWeight() {
            var light = BodyDerived.bmr(new BigDecimal("60"), new BigDecimal("175"), 30, true);
            var heavy = BodyDerived.bmr(new BigDecimal("90"), new BigDecimal("175"), 30, true);
            assertThat(heavy.subtract(light)).isEqualByComparingTo("300");
        }

        @Test
        @DisplayName("资料不全返回 null")
        void nullWhenIncomplete() {
            assertThat(BodyDerived.bmr(new BigDecimal("80"), null, 30, true)).isNull();
            assertThat(BodyDerived.bmr(new BigDecimal("80"), new BigDecimal("175"), null, true))
                    .isNull();
        }
    }

    @Nested
    @DisplayName("腰高比")
    class WaistToHeight {

        @Test
        @DisplayName("86 / 175 → 0.491，判「健康」")
        void healthy() {
            var v = BodyDerived.waistToHeight(new BigDecimal("86"), new BigDecimal("175"));
            assertThat(v).isEqualByComparingTo("0.491");
            assertThat(BodyDerived.waistToHeightLabel(v)).isEqualTo("健康");
        }

        @Test
        @DisplayName("★ 0.5 是分界，正好 0.5 算风险")
        void thresholdIsHalf() {
            // 说法是「腰围不要超过身高的一半」——「不超过」含等于
            assertThat(BodyDerived.waistToHeightLabel(new BigDecimal("0.499"))).isEqualTo("健康");
            assertThat(BodyDerived.waistToHeightLabel(new BigDecimal("0.5")))
                    .isEqualTo("中心性肥胖风险");
        }

        @Test
        @DisplayName("资料不全返回 null")
        void nullWhenIncomplete() {
            assertThat(BodyDerived.waistToHeight(null, new BigDecimal("175"))).isNull();
            assertThat(BodyDerived.waistToHeight(new BigDecimal("86"), null)).isNull();
        }
    }

    @Test
    @DisplayName("年龄由年份算，未来年份返回 null")
    void ageFrom() {
        assertThat(BodyDerived.ageFrom(1996, 2026)).isEqualTo(30);
        assertThat(BodyDerived.ageFrom(2026, 2026)).isEqualTo(0);
        assertThat(BodyDerived.ageFrom(2030, 2026)).as("未来的出生年不产生负年龄").isNull();
    }
}
