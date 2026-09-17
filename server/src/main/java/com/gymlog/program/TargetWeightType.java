package com.gymlog.program;

import lombok.Getter;

/**
 * 目标重量的表达方式。
 *
 * <p><b>为什么要支持三种，而不是只存一个公斤数</b>：
 *
 * <table border="1">
 *   <tr><th>方式</th><th>适用场景</th><th>例子</th></tr>
 *   <tr><td>绝对重量</td><td>重量固定不变</td><td>「卧推 60kg」</td></tr>
 *   <tr><td>%1RM</td><td>力量周期化</td><td>「本周 75%」——1RM 涨了重量自动跟着涨</td></tr>
 *   <tr><td>RPE</td><td>自感强度控制</td><td>「RPE 8」——状态好就多重一点</td></tr>
 * </table>
 *
 * <p>只支持绝对重量的话，用户每次 1RM 进步后都要手动改一遍计划里所有动作的重量。
 * 而 {@code %1RM} 让计划**自动跟随能力变化**——这正是周期化训练的核心需求。
 *
 * <p>{@code RPE} 更进一步：它描述的是「练到什么程度」而非「用多重」，
 * 适合状态波动大的训练者。
 */
@Getter
public enum TargetWeightType {

    /** 绝对重量：{@code target_weight} 有值 */
    ABSOLUTE("绝对重量"),

    /** 最大重量百分比：{@code target_weight_pct} 有值 */
    PERCENT_1RM("1RM 百分比"),

    /** 主观用力程度：{@code target_rpe} 有值 */
    RPE("RPE");

    private final String displayName;

    TargetWeightType(String displayName) {
        this.displayName = displayName;
    }
}
