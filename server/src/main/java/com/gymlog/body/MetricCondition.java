package com.gymlog.body;

import lombok.Getter;

/**
 * 测量条件 —— <b>同一个人的同一个指标，在不同条件下不是一个数</b>。
 *
 * <h3>为什么必须有这个字段</h3>
 *
 * <p>晨起空腹的体重和训练后（出汗、脱水）的体重可以差 1.5kg 以上，
 * 而这一天的真实变化可能只有 0.2kg。两者混在一条曲线上，
 * 用户看到的是**测量时间造成的锯齿，不是身体的变化**。
 *
 * <p>{@code METRICS 1.4} 因此要求「条件不同的点用不同形状区分」，
 * 而 {@code METRICS 1.5} 允许「统计周均值时筛选仅晨起空腹」。
 * 这两个功能都依赖这个字段，不是可选的装饰。
 *
 * <h3>不是体重专属</h3>
 *
 * <p>静息心率（{@code M6-C-1}）明确要求**晨起测量**——条件写错这个数就没意义。
 * 体脂秤的读数也随水合状态波动。所以它是所有支持条件的指标的公共字段。
 */
@Getter
public enum MetricCondition {

    /** 晨起空腹。**体重和静息心率的推荐条件**，也是唯一适合做长期趋势的 */
    FASTED("晨起空腹"),

    /** 训练后。脱水 + 糖原消耗，读数会偏低 */
    POST_WORKOUT("训练后"),

    BEFORE_BED("睡前"),

    OTHER("其他");

    private final String label;

    MetricCondition(String label) {
        this.label = label;
    }
}
