package com.gymlog.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * PR 看板（{@code METRICS 7}）。
 *
 * <h3>⚠️ 这个端点**不接受时间范围**，是故意的</h3>
 *
 * <p>PR 的语义是「**历史最高**」。带范围会得到「本季度最佳」，
 * 而 {@code METRICS 7.3} 要显示「达成日期、距今天数」——
 * 一个被范围过滤的 PR 谈「距今天数」没有意义。
 *
 * <p>而且训练总结里的 PR 判定（{@code SessionSummaryService.detectPersonalRecords}）
 * 基准也是「本次之前的历史最高」，同样是全时段的。两个接口对同一个用户
 * 给出不同的「最高」，正是这个项目最怕的那种不一致。
 *
 * <h3>{@code METRICS 7.2} 的四类 PR，本次只做两类</h3>
 *
 * <table>
 *   <tr><td>最大重量</td><td>✅ 纯 {@code MAX(weight)}</td></tr>
 *   <tr><td>最佳 e1RM</td><td>✅</td></tr>
 *   <tr><td>最多次数</td><td>❌ 「限同一重量档位」在文档里没定义</td></tr>
 *   <tr><td>最大容量</td><td>❌ 自重容量口径依赖 {@code bw_factor} + 快照体重，
 *       写进 SQL 会逼出**第三份容量口径**</td></tr>
 * </table>
 */
public record PrBoardResponse(
        List<PrCard> records
) {

    /**
     * 一张 PR 卡片。{@code METRICS 7.3}：卡片列表，**不是图表**。
     *
     * @param firstTime 首次记录即 PR。{@code METRICS 7.4} 要求标「首次记录」
     *                  且用**中性样式**——「第一次练」不是突破，渲染成庆祝
     *                  会让真正的进步贬值
     * @param daysAgo   达成距今多少天。**由服务端算**，因为「今天」是服务端的概念
     */
    public record PrCard(
            Long exerciseId,
            String exerciseName,
            String metric,
            String metricLabel,
            BigDecimal value,
            String unit,
            LocalDate achievedOn,
            long daysAgo,
            boolean firstTime
    ) {
    }
}
