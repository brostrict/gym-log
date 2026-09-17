package com.gymlog.exercise;

import lombok.Getter;

/**
 * 主要肌群。
 *
 * <p><b>为什么只有 6 个粗分类，而不是「胸大肌上束」这种细分</b>：
 * 这个枚举用于**统计维度**——「这周胸练了几组」。
 * 统计维度太细会导致每个分类的数据量都很小，看不出趋势。
 *
 * <p>细分部位（上胸/中胸/下胸）属于**动作描述**，放在动作名称和要领里，
 * 不进入统计。这是「统计维度」和「描述信息」的区别。
 */
@Getter
public enum MuscleGroup {

    CHEST("胸"),
    BACK("背"),
    LEGS("腿"),
    SHOULDERS("肩"),
    ARMS("手臂"),
    CORE("核心");

    private final String displayName;

    MuscleGroup(String displayName) {
        this.displayName = displayName;
    }
}
