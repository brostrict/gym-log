package com.gymlog.exercise;

import lombok.Getter;

/**
 * 动作的分类维度。
 *
 * <p><b>为什么只有 6 个粗分类，而不是「胸大肌上束」这种细分</b>：
 * 这个枚举用于**统计维度**——「这周胸练了几组」。
 * 统计维度太细会导致每个分类的数据量都很小，看不出趋势。
 *
 * <p>细分部位（上胸/中胸/下胸）属于**动作描述**，放在动作名称和要领里，
 * 不进入统计。这是「统计维度」和「描述信息」的区别。
 *
 * <h3>⚠️ 前 6 个是肌群，后 2 个不是</h3>
 *
 * <p>{@link #WARMUP} 和 {@link #STRETCH} 是**为了给动作分类**才借住在这个枚举里的，
 * 它们**不是肌群**，必须用 {@link #isMuscle()} 挡在统计之外。
 *
 * <p>为什么借用：{@code exercise.primary_muscle} 是 {@code NOT NULL}，
 * 而热身、拉伸类动作也需要一个分类才能被查到。为它们单独加一列
 * （{@code category}）是更正确的模型，但那是 schema 改动——
 * 在动作库还没到需要「细分部位」的规模之前，不值得。
 *
 * <p>代价是**每一个统计聚合都必须显式过滤**，而漏掉一处不会报错，
 * 只会让「拉伸」出现在「肌群周组数」平衡图里，和一个 10–20 组/周的
 * 增肌参考区间并列。所以过滤只有一处实现（{@link #isMuscle()}），
 * 且 {@code StatsService} 的聚合点是唯一调用者。
 */
@Getter
public enum MuscleGroup {

    CHEST("胸"),
    BACK("背"),
    LEGS("腿"),
    SHOULDERS("肩"),
    ARMS("手臂"),
    CORE("核心"),

    /**
     * 热身。**不是肌群。**
     *
     * <p>典型动作：开合跳、高抬腿、关节绕环、猫牛式。
     * 它们的计量类型是 {@code DURATION}——热身没有「重量×次数」。
     */
    WARMUP("热身"),

    /**
     * 拉伸。**不是肌群。**
     *
     * <p>典型动作：站姿体前屈、髋屈肌拉伸、腘绳肌拉伸。
     * 同样是 {@code DURATION}。
     *
     * <p>拉伸计入「组数」是有意义的（「今天拉伸了 6 组」），
     * 但计入**肌群平衡图**是错的——它不是练了哪块肌肉。
     */
    STRETCH("拉伸");

    private final String displayName;

    MuscleGroup(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 是不是真正的肌群。
     *
     * <p><b>统计口径（{@code METRICS 4.2} 肌群周组数）只算返回 {@code true} 的。</b>
     *
     * <p>理由：那张图回答的是「我各肌群练得均衡吗」，旁边还挂着
     * 「10–20 组/周是增肌参考区间」。把拉伸混进去会让「腿 8 组、拉伸 6 组」
     * 并列显示，而 6 组拉伸和 6 组深蹲在训练意义上毫无可比性。
     *
     * <p>这和 {@code SetType.WARMUP} 被排除在容量之外是**同一个形状的问题**：
     * 在一张用于横向比较的图上，混进不可比的量。
     */
    public boolean isMuscle() {
        return this != WARMUP && this != STRETCH;
    }

    /** 肌群列表（不含热身/拉伸）。给统计聚合用，避免各处自己写 filter */
    public static MuscleGroup[] muscles() {
        return java.util.Arrays.stream(values()).filter(MuscleGroup::isMuscle)
                .toArray(MuscleGroup[]::new);
    }
}
