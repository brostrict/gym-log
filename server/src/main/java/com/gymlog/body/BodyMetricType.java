package com.gymlog.body;

import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 身体指标的类型 —— <b>这个枚举是「身体数据有哪些指标」的唯一事实来源</b>。
 *
 * <h3>为什么要做成元数据，而不是只做一个枚举</h3>
 *
 * <p>客户端要画录入界面，就需要知道：这个指标叫什么、单位是什么、
 * 数值填多少算合理、要不要选部位、要不要填测量条件。
 *
 * <p>这些东西**服务端也必须知道**——量程校验要用它
 * （{@code BODY_METRIC_VALUE_OUT_OF_RANGE} 60002）。
 *
 * <p>如果客户端自己硬编码一份，就是同一个知识两头写：
 * 服务端把体重上限从 300 改成 400，客户端的输入框还卡在 300，
 * 用户填 350 被前端拦住、根本发不出请求，而后端其实是接受的。
 * 反过来更糟：客户端放行、后端拒绝，用户看到一句看不懂的报错。
 *
 * <p>所以有 {@code GET /body/metric-types}，返回的就是这个枚举。
 *
 * <h3>为什么单位不存库</h3>
 *
 * <p>单位由 {@code metric_type} 唯一决定，存进 {@code body_metric} 是冗余。
 * 而冗余的代价是可能不一致——同一个指标出现「kg」和「公斤」两种写法，
 * 趋势图的 Y 轴就直接裂开。需要单位的地方（图表、列表）从这里取。
 */
@Getter
public enum BodyMetricType {

    // ==================== M6-A 身体测量 ====================

    /**
     * 体重。
     *
     * <p>{@code METRICS 1}：日间波动 1–2kg，而每周真实脂肪变化约 0.5kg——
     * 信噪比 1:2 到 1:4，**噪声比信号还大**。所以体重图的主线必须是
     * 7 日移动平均，原始点只做背景。
     */
    WEIGHT("体重", "kg", 20, 400, Group.MEASURE, true, null),

    /** 围度。**必须指定部位**——「围度 100cm」没有意义 */
    CIRCUMFERENCE("围度", "cm", 10, 300, Group.MEASURE, false,
            BodySite.CIRCUMFERENCE_SITES),

    // ==================== M6-C 生理指标 ====================

    /** 静息心率。{@code M6-C-1} 要求**晨起测量**——条件写错这个数就没意义，所以 condition 有意义 */
    RESTING_HR("静息心率", "bpm", 30, 200, Group.VITAL, true, null),

    // ==================== M6-D 主观状态（1–5 分）====================
    //
    // M6-D 的注释写得很清楚：主观状态的价值在于**解释异常**。
    // 「为什么今天卧推掉了 10kg？」看一眼睡眠分和酸痛度就有答案。
    // 没有这些数据，用户会把「没睡好」误判成「训练没效果」。

    SLEEP_QUALITY("睡眠质量", "分", 1, 5, Group.SUBJECTIVE, false, null),
    SLEEP_HOURS("睡眠时长", "小时", 0, 24, Group.SUBJECTIVE, false, null),
    ENERGY_LEVEL("精力水平", "分", 1, 5, Group.SUBJECTIVE, false, null),

    /** 肌肉酸痛度。{@code M6-D-4}「可分部位」——部位**可选**，不填就是整体酸痛 */
    SORENESS("肌肉酸痛度", "分", 1, 5, Group.SUBJECTIVE, false,
            BodySite.SORENESS_SITES),

    PRE_WORKOUT_STATE("训练前状态", "分", 1, 5, Group.SUBJECTIVE, false, null),

    STRESS_LEVEL("压力水平", "分", 1, 5, Group.SUBJECTIVE, false, null);

    /** 录入界面的分组。客户端按这个把指标分节显示，不用自己编一套分类 */
    @Getter
    public enum Group {
        MEASURE("身体测量"),
        VITAL("生理指标"),
        SUBJECTIVE("主观状态");

        private final String label;

        Group(String label) {
            this.label = label;
        }
    }

    private final String label;
    private final String unit;

    /** 合理量程（含端点）。超出即 {@code BODY_METRIC_VALUE_OUT_OF_RANGE} */
    private final BigDecimal min;
    private final BigDecimal max;

    private final Group group;

    /** 是否支持测量条件（晨起空腹 / 训练后 / 睡前 / 其他） */
    private final boolean conditionSupported;

    /**
     * 允许的部位。
     *
     * <p>{@code null} = <b>该指标不允许带部位</b>（体重、体脂……）；
     * 非 null = 只允许集合内的部位。
     *
     * <p>⚠️ <b>null 和空集是两种不同的意思</b>，不要混：
     * {@code null} 是「这个指标没有部位概念」（客户端不显示选择器），
     * 而真正的「有部位概念但一个都不允许」是不存在的状态。
     * 用空集表示 null 的话，{@link #allowsSite} 会对所有部位返回 false，
     * 于是「体重填了部位」和「围度填了部位」会被同一条规则处理——
     * 前者该报「不支持部位」，后者该通过。
     */
    private final Set<BodySite> allowedSites;

    BodyMetricType(String label, String unit, int min, int max, Group group,
                   boolean conditionSupported, Set<BodySite> allowedSites) {
        this.label = label;
        this.unit = unit;
        this.min = BigDecimal.valueOf(min);
        this.max = BigDecimal.valueOf(max);
        this.group = group;
        this.conditionSupported = conditionSupported;
        this.allowedSites = allowedSites == null
                ? null
                : Collections.unmodifiableSet(new LinkedHashSet<>(allowedSites));
    }

    // ==================================================================
    // 校验 —— 全部只在这里实现一次
    // ==================================================================

    /** 这个指标有没有部位概念（或者说：客户端要不要显示部位选择器） */
    public boolean hasSites() {
        return allowedSites != null;
    }

    /**
     * 这个指标**必须**指定部位吗。
     *
     * <p>围度必须（「围度 100cm」不说明任何事），酸痛度不必（{@code M6-D-4} 是「可分部位」）。
     * 规则依据的是 {@code REQUIREMENTS M6-A-3}「围度支持只填部分部位」——
     * 「只填部分」说的是**一次可以只测几个部位**，不是「可以不写部位」。
     */
    public boolean requiresSite() {
        return this == CIRCUMFERENCE;
    }

    public boolean allowsSite(BodySite site) {
        return allowedSites != null && allowedSites.contains(site);
    }

    /**
     * 校验部位，不合法直接抛。
     *
     * <p>⚠️ <b>这个校验不能省。</b>没有它，同一个部位会出现
     * 「左大臂」和「左上臂」两种写法，而趋势图的切换器是按 site 值分组的——
     * 用户会看到两个都叫「左大臂」的选项，各有一半的数据。
     * 录得进去、看不出错，只是图永远画不全。
     */
    public void validateSite(BodySite site) {
        if (!hasSites()) {
            if (site != BodySite.NONE) {
                throw new BizException(ErrorCode.BODY_METRIC_SITE_NOT_ALLOWED,
                        label + "没有部位概念");
            }
            return;
        }
        if (site == BodySite.NONE) {
            if (requiresSite()) {
                throw new BizException(ErrorCode.BODY_METRIC_SITE_REQUIRED,
                        label + "必须指定部位");
            }
            return;
        }
        if (!allowsSite(site)) {
            throw new BizException(ErrorCode.BODY_METRIC_SITE_NOT_ALLOWED,
                    label + "不支持部位「" + site.getLabel() + "」");
        }
    }

    /**
     * 校验数值量程。
     *
     * <p>服务端必须自己验，不能只靠客户端输入框——
     * 客户端可以绕过，而且离线队列重放时根本没人拦。
     */
    public void validateValue(BigDecimal value) {
        if (value == null) {
            throw new BizException(ErrorCode.BODY_METRIC_VALUE_OUT_OF_RANGE,
                    label + "的数值不能为空");
        }
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new BizException(ErrorCode.BODY_METRIC_VALUE_OUT_OF_RANGE,
                    label + "应在 " + plain(min) + "–" + plain(max) + unitPart() + "之间");
        }
    }

    /**
     * 校验测量条件。
     *
     * <p>不支持条件的指标带了条件**不算错**，直接忽略——
     * 客户端版本不一致时（老客户端多传一个字段）不该让用户录不进去。
     * 但支持条件的指标不做限制，取值由 {@link MetricCondition} 保证。
     */
    public boolean acceptsCondition() {
        return conditionSupported;
    }

    /** 去掉 BigDecimal 的小数尾巴：量程是 20 不是 20.00，报错文案里不该出现后者 */
    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    /**
     * 单位在报错文案里的写法。
     *
     * <p>中文单位（分 / 级 / 小时）直接贴着数字，英文单位（kg / cm / bpm）
     * 两边留空格。统一加空格的话会印出「1–5 分 之间」——
     * 中文里数字和单位之间不该有空格，而报错文案是**直接给用户看的**。
     */
    private String unitPart() {
        boolean ascii = unit.chars().allMatch(c -> c < 128);
        return ascii ? " " + unit + " " : unit;
    }
}
