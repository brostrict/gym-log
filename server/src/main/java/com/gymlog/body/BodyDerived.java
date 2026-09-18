package com.gymlog.body;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 从**自测数据**推导出的指标 —— 纯函数，无依赖无状态。
 *
 * <h3>为什么这些是「算」的，不是「录」的</h3>
 *
 * <p>V16 把体脂率 / 骨骼肌量 / 水分率 / BMR / 内脏脂肪五项从录入项里去掉了，
 * 判据是「**用户能不能自己测**」。那五项是体脂秤用生物电阻抗 + 公式推算的，
 * 不同品牌差 3–5 个百分点，用户无法独立验证。
 *
 * <p>但「秤推的」和「我们推的」有一个本质区别：<b>公式是公开的</b>。
 * 用户能看到 BMI 是怎么从体重和身高算出来的，也能自己拿计算器验一遍。
 * 误差仍然有（Deurenberg 体脂率约 ±5%），但它是**可知且一致**的，
 * 而且**换设备不会跳变**——因为它根本不依赖设备。
 *
 * <p>一句话：不反对估算，反对**黑箱估算**。
 *
 * <h3>⚠️ 这些值一律实时算，不落库</h3>
 *
 * <p>派生值没有独立的测量时刻——体重一变它就该变。存下来的话就要处理
 * 「体重改了，派生的怎么办」，那是个没有正确答案的问题：
 * 重算会让历史值变化（违反快照语义），不重算又会让图上出现和体重对不上的数。
 *
 * <p>而实时算的成本是零：体重序列本来就要查出来。
 *
 * <h3>⚠️ 为什么不出「BMI 趋势图」</h3>
 *
 * <p>{@code METRICS 0.1} 的准入标准是「答不出『看完这张图我会改变什么行为』的图，不做」。
 *
 * <p>BMI = 体重 / 身高²，而身高是常数——所以 <b>BMI 曲线和体重曲线形状完全一样</b>，
 * 只是纵轴刻度不同；体脂率估算在年龄不变的短期内同理；BMR 也由体重决定。
 * 三条曲线提供的信息和体重曲线**完全等价**。
 *
 * <p>所以派生指标只给「当前值 + 解读」——那是体重曲线给不了的东西
 * （「BMI 23.4，正常」比「体重 73.3kg」多了一层判断）。
 */
public final class BodyDerived {

    /** 中国成人 BMI 分级（WS/T 428-2013）。和 WHO 的标准不同，别混用 */
    private static final BigDecimal BMI_UNDERWEIGHT = new BigDecimal("18.5");
    private static final BigDecimal BMI_OVERWEIGHT = new BigDecimal("24");
    private static final BigDecimal BMI_OBESE = new BigDecimal("28");

    /** 腰高比阈值。{@code METRICS} 与 WHO 一致：0.5 是中心性肥胖的分界 */
    private static final BigDecimal WHTR_RISK = new BigDecimal("0.5");

    private BodyDerived() {
    }

    // ==================================================================
    // 公式
    // ==================================================================

    /**
     * BMI = 体重(kg) / 身高(m)²。
     *
     * @param heightCm 身高，厘米
     * @return 保留一位小数；参数不全时返回 {@code null}
     */
    public static BigDecimal bmi(BigDecimal weightKg, BigDecimal heightCm) {
        if (weightKg == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        BigDecimal heightM = heightCm.movePointLeft(2);
        return weightKg.divide(heightM.multiply(heightM), 1, RoundingMode.HALF_UP);
    }

    /**
     * 体脂率估算 —— <b>Deurenberg 公式</b>。
     *
     * <pre>
     *   BF% = 1.20 × BMI + 0.23 × 年龄 − 10.8 × 性别 − 5.4
     *   性别：男 = 1，女 = 0
     * </pre>
     *
     * <p><b>它有多准</b>：对群体均值误差约 ±4–5 个百分点，个体上可能更差。
     * 它<u>不能</u>替代 DEXA 或水下称重。但作为**趋势**参考是够用的——
     * 而趋势恰恰是用户真正要看的东西。
     *
     * <p>⚠️ 公式里的性别是 1/0，不是 2/1。照着 {@code User.GENDER_MALE = 1}
     * 直接代入是巧合对的，用 {@code GENDER_FEMALE = 2} 代入就错了
     * （会算出比男性还低的值）。所以这里显式映射。
     *
     * @param male true = 男。{@code null} 表示性别未设置 → 返回 {@code null}
     */
    public static BigDecimal bodyFatPercent(BigDecimal weightKg, BigDecimal heightCm,
                                            Integer age, Boolean male) {
        BigDecimal bmi = bmi(weightKg, heightCm);
        if (bmi == null || age == null || male == null) {
            return null;
        }
        BigDecimal sexTerm = male ? new BigDecimal("10.8") : BigDecimal.ZERO;
        BigDecimal result = new BigDecimal("1.20").multiply(bmi)
                .add(new BigDecimal("0.23").multiply(BigDecimal.valueOf(age)))
                .subtract(sexTerm)
                .subtract(new BigDecimal("5.4"));

        // 公式在极端输入下会给出负数（很瘦的年轻人）或 >100%（很胖的人）。
        // 那两种值显示出来只会让人困惑，所以夹到生理上可能的范围。
        if (result.signum() < 0) {
            return BigDecimal.ZERO.setScale(1);
        }
        if (result.compareTo(new BigDecimal("100")) > 0) {
            return new BigDecimal("100.0");
        }
        return result.setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 基础代谢率 —— <b>Mifflin-St Jeor 公式</b>。
     *
     * <pre>
     *   男：BMR = 10 × 体重(kg) + 6.25 × 身高(cm) − 5 × 年龄 + 5
     *   女：BMR = 10 × 体重(kg) + 6.25 × 身高(cm) − 5 × 年龄 − 161
     * </pre>
     *
     * <p><b>为什么用这个而不是 Harris-Benedict</b>：Mifflin-St Jeor 是 1990 年
     * 针对现代人群重新拟合的，在正常体重和超重人群上误差更小，
     * 目前是营养学界推荐的首选。Harris-Benedict 会系统性高估约 5%。
     *
     * <p>它算的是「躺着不动消耗多少」，不是每日总消耗。要算总消耗还得乘
     * 活动系数——那是另一个指标（TDEE），需要知道用户的训练频率，
     * <b>本步不做</b>。
     */
    public static BigDecimal bmr(BigDecimal weightKg, BigDecimal heightCm,
                                 Integer age, Boolean male) {
        if (weightKg == null || heightCm == null || age == null || male == null) {
            return null;
        }
        BigDecimal base = new BigDecimal("10").multiply(weightKg)
                .add(new BigDecimal("6.25").multiply(heightCm))
                .subtract(new BigDecimal("5").multiply(BigDecimal.valueOf(age)))
                .add(male ? new BigDecimal("5") : new BigDecimal("-161"));

        // 生理上不可能低于这个数；算出来更低说明输入有问题，
        // 但返回 null 比返回一个荒谬的值好
        return base.signum() <= 0 ? null : base.setScale(0, RoundingMode.HALF_UP);
    }

    /**
     * 腰高比 WHtR = 腰围 / 身高。
     *
     * <p><b>它比 BMI 更能说明问题。</b>BMI 分不清肌肉和脂肪——
     * 一个 90kg 的健美运动员 BMI 会显示「肥胖」。而腰高比只看腰围，
     * 直接反映**中心性脂肪**，那才是和代谢风险相关的那部分。
     *
     * <p>阈值 0.5 的说法是「腰围不要超过身高的一半」，好记且跨人群适用
     * （BMI 的分级标准各国不同，腰高比不用分）。
     *
     * <p>⚠️ 腰围要用**自测**的那条（{@code CIRCUMFERENCE + WAIST}）——
     * 这正是 V16 保留围度、去掉体脂率的同一个判据。
     */
    public static BigDecimal waistToHeight(BigDecimal waistCm, BigDecimal heightCm) {
        if (waistCm == null || heightCm == null || heightCm.signum() <= 0) {
            return null;
        }
        return waistCm.divide(heightCm, 3, RoundingMode.HALF_UP);
    }

    // ==================================================================
    // 解读 —— 「23.4」本身没有意义，用户要的是「正常」
    // ==================================================================

    /** BMI 分级（中国标准）。返回给用户看的短语 */
    public static String bmiLabel(BigDecimal bmi) {
        if (bmi == null) {
            return null;
        }
        if (bmi.compareTo(BMI_UNDERWEIGHT) < 0) {
            return "偏瘦";
        }
        if (bmi.compareTo(BMI_OVERWEIGHT) < 0) {
            return "正常";
        }
        if (bmi.compareTo(BMI_OBESE) < 0) {
            return "超重";
        }
        return "肥胖";
    }

    /** 体脂率分级。男女阈值不同——同一个 22% 对男性是偏高，对女性是偏低 */
    public static String bodyFatLabel(BigDecimal percent, Boolean male) {
        if (percent == null || male == null) {
            return null;
        }
        double p = percent.doubleValue();
        if (male) {
            if (p < 6) return "过低";
            if (p < 14) return "运动员";
            if (p < 18) return "健康";
            if (p < 25) return "偏高";
            return "肥胖";
        }
        if (p < 14) return "过低";
        if (p < 21) return "运动员";
        if (p < 25) return "健康";
        if (p < 32) return "偏高";
        return "肥胖";
    }

    /** 腰高比分级 */
    public static String waistToHeightLabel(BigDecimal ratio) {
        if (ratio == null) {
            return null;
        }
        return ratio.compareTo(WHTR_RISK) < 0 ? "健康" : "中心性肥胖风险";
    }

    /** 由出生年和「今年」算年龄。{@code today} 必须是参数——纯函数里不许调 now() */
    public static Integer ageFrom(int birthYear, int currentYear) {
        int age = currentYear - birthYear;
        return age < 0 ? null : age;
    }
}
