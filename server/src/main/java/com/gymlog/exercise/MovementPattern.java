package com.gymlog.exercise;

import lombok.Getter;

/**
 * 动作模式 —— 按「身体怎么动」分类，与器械和肌群都正交。
 *
 * <p><b>这个分类的实用价值：找替代动作</b>。
 *
 * <p>当深蹲架被占了，用户需要一个替代动作。按肌群找会推荐出
 * 「腿举」这种固定器械动作，但按动作模式找会推荐「高脚杯深蹲」
 * 「箭步蹲」——**同样的运动模式，训练效果更接近**。
 *
 * <p>运动科学上，动作模式是比肌群更本质的分类：
 * 它决定了关节角度、发力顺序和神经适应。
 */
@Getter
public enum MovementPattern {

    /** 水平推：卧推、俯卧撑 */
    HORIZONTAL_PUSH("水平推"),

    /** 水平拉：划船、反向飞鸟 */
    HORIZONTAL_PULL("水平拉"),

    /** 垂直推：推举、倒立撑 */
    VERTICAL_PUSH("垂直推"),

    /** 垂直拉：引体向上、高位下拉 */
    VERTICAL_PULL("垂直拉"),

    /** 蹲：深蹲、腿举、箭步蹲 */
    SQUAT("蹲"),

    /** 髋铰链：硬拉、臀桥、罗马尼亚硬拉 */
    HINGE("髋铰链"),

    /** 弓步：保加利亚分腿蹲、行走箭步蹲 */
    LUNGE("弓步"),

    /** 核心：平板支撑、卷腹、死虫 */
    CORE("核心"),

    /** 负重行走：农夫行走、 suitcase carry */
    CARRY("负重行走");

    private final String displayName;

    MovementPattern(String displayName) {
        this.displayName = displayName;
    }
}
