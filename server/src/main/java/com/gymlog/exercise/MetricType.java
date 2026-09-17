package com.gymlog.exercise;

import lombok.Getter;

/**
 * 计量类型 —— 决定一个动作「怎么记」。
 *
 * <p>这是动作库最重要的一个属性。它决定了：
 * <ul>
 *   <li>组记录（{@code SetRecord}）里哪几个字段有意义</li>
 *   <li>训练容量的计算公式</li>
 *   <li>跟练界面上显示哪些输入控件</li>
 * </ul>
 *
 * <p><b>设计取舍：为什么用「一个字段 + 多个可空列」，而不是每种类型建一张表</b>
 *
 * <p>组记录里同时存在 {@code weight / reps / duration_sec / distance_m} 四个可空列，
 * 由本枚举决定哪个有意义。
 *
 * <p>另外两种方案都有明显缺陷：
 * <pre>
 *   方案A：每种计量类型建一张表（weight_set / duration_set / ...）
 *          → 查「某次训练的所有组」要 UNION 四张表，
 *            统计容量时要分情况处理，代码里到处是 if-else
 *
 *   方案B：EAV 模型（一个键值对表存所有属性）
 *          → 灵活但查询灾难。「取最近 10 次的最佳组」这种需求
 *            会变成多层自连接，性能和可读性都很差
 * </pre>
 *
 * <p>宽表 + 判别器的代价是「有可空列」，但换来了查询的简单——
 * 所有统计 SQL 都是单表操作。新增计量方式（如「负重引体」）
 * 只需加一列，不影响已有查询。
 */
@Getter
public enum MetricType {

    /**
     * 重量 × 次数。
     *
     * <p>有意义的字段：{@code weight}、{@code reps}
     * <br>容量公式：{@code Σ(weight × reps)}
     * <br>典型动作：杠铃卧推、哑铃弯举、腿举
     */
    WEIGHT_REPS("重量×次数"),

    /**
     * 仅次数（自重动作）。
     *
     * <p>有意义的字段：{@code reps}
     * <br>容量公式：{@code Σ(体重 × bw_factor × reps)}
     * —— 需要动作上有 {@code bw_factor}
     * <br>典型动作：引体向上、俯卧撑、双杠臂屈伸
     */
    REPS_ONLY("仅次数"),

    /**
     * 仅时长（等长收缩）。
     *
     * <p>有意义的字段：{@code durationSec}
     * <br>容量：**不计入**。时长无法折算成「重量×次数」，单独统计总时长
     * <br>典型动作：平板支撑、死吊、靠墙静蹲
     */
    DURATION("仅时长"),

    /**
     * 距离 + 时长（有氧）。
     *
     * <p>有意义的字段：{@code distanceM}、{@code durationSec}
     * <br>容量：**不计入**，单独统计距离与时长
     * <br>典型动作：跑步、划船机、动感单车
     */
    DISTANCE_DURATION("距离+时长");

    /** 给前端显示的中文名称 */
    private final String displayName;

    MetricType(String displayName) {
        this.displayName = displayName;
    }

    /** 该计量方式是否参与训练容量统计 */
    public boolean countsTowardVolume() {
        return this == WEIGHT_REPS || this == REPS_ONLY;
    }
}
