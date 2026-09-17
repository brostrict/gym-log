package com.gymlog.exercise;

import lombok.Getter;

/**
 * 器械类型。
 *
 * <p><b>这个分类解决什么实际问题</b>：「健身房器械被占了怎么办」
 * 「出差住酒店只有哑铃怎么练」——用户需要按器械筛选动作。
 *
 * <p>这也是**徒手计划模板**的基础：{@code BODYWEIGHT} 分类下的动作
 * 构成零器械训练计划，让计划不会因为环境限制而中断。
 */
@Getter
public enum Equipment {

    /** 杠铃。需要深蹲架/卧推凳等配套 */
    BARBELL("杠铃"),

    /** 哑铃。最灵活的自由重量 */
    DUMBBELL("哑铃"),

    /** 固定器械。轨迹固定，适合新手和力竭组 */
    MACHINE("固定器械"),

    /** 绳索/龙门架。角度可调，张力恒定 */
    CABLE("绳索"),

    /** 自重。零器械，居家/出差可用 */
    BODYWEIGHT("自重"),

    /** 壶铃 */
    KETTLEBELL("壶铃"),

    /** 弹力带。居家常用，阻力随拉伸变化 */
    BAND("弹力带"),

    /** 其他（药球、TRX、雪橇等） */
    OTHER("其他");

    private final String displayName;

    Equipment(String displayName) {
        this.displayName = displayName;
    }
}
