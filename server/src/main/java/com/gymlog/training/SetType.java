package com.gymlog.training;

import lombok.Getter;

/**
 * 组的类型。
 *
 * <p><b>放在 {@code training} 包而不是 {@code program} 包</b>：
 * 计划（{@code prescribed_set}）和实际执行（{@code set_record}）都要用它。
 * 它描述的是「这一组是什么性质」，属于训练领域的通用概念，
 * 不属于计划或执行的任何一方。
 */
@Getter
public enum SetType {

    /**
     * 热身组。
     *
     * <p><b>⚠️ 所有训练容量统计都必须排除它</b>——
     * 热身组重量轻、次数多，混进去会让所有趋势图失真。
     * 这是本项目最容易出错的一处（见 METRICS.md 的通用约定）。
     */
    WARMUP("热身组"),

    /** 正式组：真正计入训练量的组 */
    WORKING("正式组"),

    /** 力竭组：做到无法再完成一次标准动作 */
    FAILURE("力竭组"),

    /** 递减组：一组做到力竭后立即减重继续做 */
    DROP("递减组");

    private final String displayName;

    SetType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 是否计入训练容量。
     *
     * <p>把规则放在枚举里，而不是散落在各个统计 SQL 里——
     * 这样「什么算正式组」只有一个定义处。
     * 如果哪天要调整规则（比如把力竭组单独统计），只改这里。
     */
    public boolean countsTowardVolume() {
        return this != WARMUP;
    }
}
