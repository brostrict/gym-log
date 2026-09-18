package com.gymlog.body;

import com.gymlog.stats.MovingAverage;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 身体数据的序列加工 —— 纯函数，无依赖无状态。
 *
 * <h3>它只做一件事：把原始测量压成「一天一个点」</h3>
 *
 * <p>{@code METRICS 1.2} 的移动平均是**两步式**的，顺序不能颠倒：
 * <pre>
 *   ① 日值(d) = 该日所有测量值的算术平均     ← 这一步在本类
 *   ② MA7(d)  = 最近 7 个自然日内所有日值的平均  ← 在 {@link MovingAverage}
 * </pre>
 *
 * <p>第二步直接复用 4.1 写好的 {@link MovingAverage}，**不另写一份**。
 * 那个类的注释整段都在讲体重（「日间波动 1–2kg，信噪比 1:2 到 1:4」），
 * 本来就是为这里写的——只是 4A 的周统计用不到它，所以它一直
 * **零生产调用者**，只有单测。4B 是它的第一个真实调用点。
 *
 * <h3>为什么第 ① 步值得单独一个类</h3>
 *
 * <p>因为它有一个**容易写反**的语义：同一天测三次，
 * 那天的权重**不能**变成三倍。{@code METRICS 1.3} 整节论证的是「压掉日内噪声」，
 * 而按测量次数加权等于「越勤快测的人曲线越抖」，与目的直接矛盾。
 *
 * <h3>⚠️ 时区：「自然日」是 Asia/Shanghai 的自然日</h3>
 *
 * <p>{@code METRICS 0.2} 写「时间统一存 UTC」，但本项目实际是
 * {@code LocalDateTime} + Jackson {@code Asia/Shanghai} + 全链路零转换
 * （JDBC 串里 {@code serverTimezone=Asia/Shanghai}）。所以
 * {@code measuredAt.toLocalDate()} 拿到的**就是**上海的自然日。
 *
 * <p>这是个已知的文档与实现差异，**明确不做 per-user 时区**：
 * 一个健身 App 的用户不会跨时区记录体重，而引入用户时区要动
 * 整个项目的日期处理方式，收益与代价完全不成比例。
 * （已在 METRICS 0.2 标注）
 */
public final class BodySeries {

    /**
     * 原始测量点，保留测量条件——
     * {@code METRICS 1.4} 要求「条件不同的点用不同形状区分」，那是**原始点**的属性，
     * 日均值不带条件（一天里可能既有晨起也有训练后）。
     */
    public record RawPoint(
            LocalDateTime measuredAt,
            BigDecimal value,
            MetricCondition condition
    ) {
    }

    private BodySeries() {
    }

    /**
     * 按**自然日**取均值。
     *
     * @param raw 原始测量，顺序任意（内部会分组，不依赖输入顺序）
     * @return 每天一个点，按日期升序。**用 TreeMap 保序**——
     *         {@code HashMap} 的话 JSON 里日期顺序随机，
     *         客户端 X 轴会画成锯齿，而且断言会 flaky
     */
    public static List<MovingAverage.DailyPoint> dailyAverage(List<BodyMetric> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }

        // 先按日期归组，累加和计数——避免存一个 List 再求平均
        Map<LocalDate, BigDecimal> sums = new TreeMap<>();
        Map<LocalDate, Integer> counts = new TreeMap<>();

        for (BodyMetric m : raw) {
            LocalDate day = m.getMeasuredAt().toLocalDate();
            sums.merge(day, m.getValue(), BigDecimal::add);
            counts.merge(day, 1, Integer::sum);
        }

        List<MovingAverage.DailyPoint> result = new ArrayList<>(sums.size());
        for (Map.Entry<LocalDate, BigDecimal> e : sums.entrySet()) {
            int n = counts.get(e.getKey());
            result.add(new MovingAverage.DailyPoint(
                    e.getKey(),
                    e.getValue().divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP)));
        }
        return result;
    }

    /**
     * 把原始测量转成响应点，**保持时间升序**。
     *
     * <p>条件的处理：不支持条件的指标，库里存的是 NULL，
     * 原样透传成 {@code null} 即可——客户端按 null 画默认形状。
     * 这里不做「翻译成 OTHER」之类的兜底：那会让「没填」和「填了其他」
     * 在图上长得一样，而它们是可以区分的两种状态。
     */
    public static List<RawPoint> toRawPoints(List<BodyMetric> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<RawPoint> points = new ArrayList<>(raw.size());
        for (BodyMetric m : raw) {
            points.add(new RawPoint(m.getMeasuredAt(), m.getValue(),
                    m.getMeasureCondition()));
        }
        return points;
    }

    /**
     * 某个部位的最新值 → 某些指标（围度）要显示「距上次 ±N」。
     *
     * <p>返回 {@code null} 表示没有数据，调用方要处理——
     * 不要返回 {@code BigDecimal.ZERO}：那会让「没测过」显示成「变化 0」。
     */
    public static BigDecimal latestValue(List<BodyMetric> raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        return raw.get(raw.size() - 1).getValue();
    }

    /**
     * 一组部位里「哪些有数据」，按 {@link BodySite} 的声明顺序返回。
     *
     * <p>{@code METRICS 2.5}：只测了部分部位时，**只显示有数据的部位**，
     * 不显示空图。所以客户端需要一个「有数据的部位」列表来画切换器，
     * 而不是把 12 个部位全列出来、11 个点进去是空的。
     *
     * <p>顺序取 {@link BodySite#CIRCUMFERENCE_SITES} 的声明顺序而不是
     * 数据里的出现顺序：切换器的顺序应该是**解剖学顺序**（颈胸腰臀、然后四肢），
     * 按出现顺序的话，用户前几次只测了腰围，腰围就永远排在第一个。
     */
    public static List<BodySite> usedSites(List<String> siteCodes, List<BodySite> orderedBy) {
        if (siteCodes == null || siteCodes.isEmpty()) {
            return List.of();
        }
        Map<String, Boolean> present = new LinkedHashMap<>();
        for (String code : siteCodes) {
            present.put(code, Boolean.TRUE);
        }
        List<BodySite> result = new ArrayList<>();
        for (BodySite site : orderedBy) {
            if (present.containsKey(site.name())) {
                result.add(site);
            }
        }
        return result;
    }
}
