package com.gymlog.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 周粒度的统计序列 —— 一个请求喂三张图。
 *
 * <h3>为什么容量 / 组数 / 频率合并成一个端点</h3>
 *
 * <p>三者都是「区间内每周一个桶」，而且**要遍历同一批 {@code set_record}**。
 * 分成三个端点意味着：三次相同的批查、三份零填充逻辑、三个可能互相不一致的周列表
 * ——用户会看到三张图的 X 轴对不齐。
 *
 * <p>合并之后：一次加载、一次零填充、三条序列必然对齐。
 *
 * <h3>⚠️ 周列表是「按日历铺开」的，空周也在</h3>
 *
 * <p>{@code METRICS 4.6}：「某周完全没练 → 显示为 **0 高度的空柱，不跳过**」。
 * 跳过空周会把「中断三周」画成「连续三周」，正好掩盖了最该看见的东西。
 *
 * <p>反过来，测量类序列（体重、e1RM）**不能**零填充（{@code METRICS 0.2}
 * 「缺失数据不插值」）——那是「没测」，不是「值为 0」。
 * 区别在于：**计数类的 0 是真实测量值，测量类的 0 是编出来的**。
 */
public record WeeklyStatsResponse(
        LocalDate from,
        LocalDate to,
        /**
         * 连续训练周数（streak）。**由服务端算，客户端不自己数。**
         *
         * <p>「连续几周」是一条口径规则（当前周怎么算、缺一周算不算断），
         * 不是把数组长度一数就完事的。放在这里是因为它只依赖
         * {@code weeks} 里的 {@code sessionCount}——单独开一个端点
         * 会让两张图的数据来自两次查询，可能互相矛盾。
         *
         * <p>实现见 {@link com.gymlog.stats.WeekSeries#currentStreak}。
         */
        int currentStreak,
        List<WeekBucket> weeks
) {

    /**
     * 一周的统计。
     *
     * @param currentWeek    是不是当前这一周。{@code METRICS 5.5} 要求它半透明显示
     *                       ——用户需要一眼看出「这周才周三，柱子矮是正常的」
     * @param sessionCount   该周完成了几场训练（含临时训练，{@code METRICS 5.5}）
     * @param complianceRate 符合率 0–100。**该周没有计划内的组时为 null**
     */
    public record WeekBucket(
            LocalDate weekStart,
            boolean currentWeek,
            BigDecimal volume,
            int workingSets,
            int sessionCount,
            BigDecimal complianceRate,
            List<MuscleSets> muscleSets
    ) {
    }

    /**
     * 某个肌群在一周里的组数。
     *
     * <p><b>固定 6 项，含 0。</b>堆叠柱/横条要的是「每个肌群占一格」，
     * 缺项会让客户端的颜色映射错位——某周没练腿时，腿的紫色跑到肩上去。
     *
     * <p>顺带带上中文名：否则客户端要硬编码第二份肌群词表，
     * 而 {@code MuscleGroup} 的显示名已经在服务端有一份了。
     */
    public record MuscleSets(
            String muscle,
            String label,
            int sets
    ) {
    }
}
