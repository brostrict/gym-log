package com.gymlog.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 日历窗口移动平均 —— 纯函数，无依赖无状态。
 *
 * <h3>为什么体重必须做移动平均</h3>
 *
 * <p>{@code METRICS 1.3}：日间体重波动 1–2kg（水分、糖原、钠摄入），
 * 而每周真实脂肪变化约 0.5kg。**信噪比 1:2 到 1:4——噪声比信号还大。**
 * 原始日点图会让用户看到某天涨了 1.5kg 就去减餐、加有氧。
 *
 * <h3>⚠️ 「最近 7 天」是<u>日历窗口</u>，不是「最近 7 个数据点」</h3>
 *
 * <p>这是最容易写错的一处。按「最近 7 个点」算的话，一周只称两次的人，
 * 窗口实际覆盖 3.5 周——曲线会变得又平又滞后，而它要压的是**日内噪声**。
 *
 * <h3>⚠️ 先按日取均值，再求窗口平均</h3>
 *
 * <p>{@code METRICS 1.2} 写「MA7 = 最近 7 天内**所有测量值**的算术平均」，
 * 1.5 又写「单日多次测量 → **取该日平均值**作为当天的点」。两处读法不同，
 * 这里取后者，并把 1.2 改成两步式。
 *
 * <p>理由不只是「一天测三次权重会变三倍」——{@code METRICS 1.3} 整节论证的是
 * 「压掉日内噪声」。若同一天多测几次就把那天权重放大，
 * **越勤快测量的人曲线反而越抖**，与 1.3 的目的直接矛盾。
 *
 * <p>所以本类**要求输入已经按日聚合过**（每天最多一个点）。
 * 这是调用方的责任，不是这里的——聚合本身要读库。
 */
public final class MovingAverage {

    /** 少于这个数量的日均值点，整条 MA 线不显示（{@code METRICS 1.5}、{@code AC-7-2}） */
    public static final int MIN_POINTS_FOR_MA = 3;

    /**
     * 窗口内至少要有这么多点，该点的 MA 才算有效。
     *
     * <p>窗口里只有一个点时，「平均」就是那个点本身——它没有压掉任何噪声，
     * 却会让曲线看起来「有移动平均」。返回 null（不可用）比返回一个
     * 假装平滑的值诚实。
     */
    public static final int MIN_POINTS_IN_WINDOW = 2;

    private MovingAverage() {
    }

    /** 原始点：某一天的均值。{@code date} 在列表中必须唯一。 */
    public record DailyPoint(LocalDate date, BigDecimal value) {
    }

    /**
     * 结果点。{@code ma} 为 {@code null} 表示**该点没有可用的移动平均**
     * （窗口内点数不足），不是「平均值为 0」。
     */
    public record MaPoint(LocalDate date, BigDecimal value, BigDecimal ma) {
    }

    /**
     * 对日均值序列做日历窗口移动平均（滑动窗口，O(n)）。
     *
     * @param dailyPoints 按日聚合后的点，每天至多一个
     * @param windowDays  窗口天数（7 / 28）
     * @return 与输入等长的结果，升序；{@code ma} 可能为 null
     */
    public static List<MaPoint> rolling(List<DailyPoint> dailyPoints, int windowDays) {
        if (windowDays < 1) {
            throw new IllegalArgumentException("窗口天数必须 >= 1，收到 " + windowDays);
        }
        if (dailyPoints == null || dailyPoints.isEmpty()) {
            return List.of();
        }

        // 防御性排序：调用方传进来的顺序不可信，而顺序错了**结果会静默错误**——
        // 滑动窗口的前提就是按日期升序。n 很小，排一下不值钱。
        List<DailyPoint> points = new ArrayList<>(dailyPoints);
        points.sort(Comparator.comparing(DailyPoint::date));

        List<MaPoint> result = new ArrayList<>(points.size());
        int left = 0;
        BigDecimal sum = BigDecimal.ZERO;

        for (int right = 0; right < points.size(); right++) {
            sum = sum.add(points.get(right).value());

            // 窗口是 [d - (window-1), d]，闭区间
            LocalDate windowStart = points.get(right).date().minusDays(windowDays - 1L);
            while (points.get(left).date().isBefore(windowStart)) {
                sum = sum.subtract(points.get(left).value());
                left++;
            }

            int inWindow = right - left + 1;
            BigDecimal ma = inWindow >= MIN_POINTS_IN_WINDOW
                    ? sum.divide(BigDecimal.valueOf(inWindow), 2, RoundingMode.HALF_UP)
                    : null;

            result.add(new MaPoint(points.get(right).date(), points.get(right).value(), ma));
        }
        return result;
    }

    /**
     * 数据够不够画移动平均线（{@code METRICS 1.5}「数据点 &lt; 3 天 → 不显示移动平均线」）。
     *
     * <p><b>必须由这个方法和 {@link #rolling} 的规则共同决定，不能另写一个判断。</b>
     * 否则会出现「线和提示互相矛盾」：画了线却提示「数据不足」，
     * 或者反过来——用户会觉得整个图表在乱说。
     */
    public static boolean enoughDataForMa(List<DailyPoint> dailyPoints) {
        return dailyPoints != null && dailyPoints.size() >= MIN_POINTS_FOR_MA;
    }
}
