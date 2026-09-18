package com.gymlog.stats.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 单动作的估算 1RM 曲线（{@code METRICS 3.4}）。
 *
 * <p>这是**手机端的主力入口**——{@code M7-B} 写明「健身房里最高频的查询
 * 就是『我上次这个动作做多重』，PC 上没人会为了这个开电脑」。
 *
 * <h3>三个必须遵守的规则（{@code METRICS 3.3}）</h3>
 *
 * <pre>
 *   取每次训练的【最佳组】，不是全部组的平均
 *   仅 reps ≤ 12 参与计算（Epley 在 12 次以上严重高估）
 *   仅 WEIGHT_REPS 动作有意义——自重和时长类没有「1RM」这个概念
 * </pre>
 *
 * @param supported   动作是否支持 e1RM。{@code false} 时 {@code points} 为空，
 *                    客户端应显示「这个动作没有 1RM 概念」而不是一张空图
 * @param allTimeBest 历史最高 e1RM，画参考虚线用。**无界查询**——
 *                    复用 {@code bestE1rmBefore}，不新写公式
 */
public record ExerciseE1rmResponse(
        Long exerciseId,
        String exerciseName,
        String metricType,
        boolean supported,
        BigDecimal allTimeBest,
        List<E1rmPoint> points
) {

    /**
     * 一次训练的数据点。
     *
     * @param bestE1rm 该次训练的最佳组（主线）
     * @param sets     该次全部有效组（散点，展示当天的离散程度）
     */
    public record E1rmPoint(
            LocalDate date,
            BigDecimal bestE1rm,
            List<SetPoint> sets
    ) {
    }

    /**
     * 一组的数据（散点 + 长按读数）。
     *
     * <p><b>为什么是对象而不是裸的三个数组</b>：{@code application.yml} 里有
     * {@code spring.jackson.default-property-inclusion: non_null}，它设的是
     * value **和 content** 两者——而 <b>content inclusion 管集合元素</b>。
     * 裸数组里出现 {@code null} 会被静默丢掉，点位整体左移、X 轴全错，不报任何错。
     * 用对象就没有这个问题（缺字段是缺字段，不是丢元素）。
     */
    public record SetPoint(
            BigDecimal weight,
            int reps,
            BigDecimal e1rm
    ) {
    }
}
