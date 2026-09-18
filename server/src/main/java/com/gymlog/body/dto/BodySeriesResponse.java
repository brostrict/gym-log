package com.gymlog.body.dto;

import com.gymlog.body.BodyMetricType;
import com.gymlog.body.BodySite;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 身体数据的趋势序列 —— 一张图要的全部数据。
 *
 * <h3>三条序列，各有各的用途</h3>
 * <pre>
 *   rawPoints    每次测量（浅色小点，背景）—— 带测量条件，用于区分形状
 *   dailyPoints  按自然日取均值（METRICS 1.2 第一步）
 *   maPoints     7 日移动平均（主线，视觉权重最高）
 * </pre>
 *
 * <p><b>为什么 raw 和 daily 都要发</b>：它们回答不同的问题。
 * raw 是「我那天到底站上秤看到了几」，daily 是「那天代表值是多少」。
 * 一天测三次的人，raw 有三个点、daily 只有一个——这正是
 * {@code METRICS 1.2} 两步式的意义，客户端不该自己再做一遍这个合并。
 *
 * <h3>⚠️ 画移动平均线只需要遵守一条规则：{@code ma == null} 就不画</h3>
 *
 * <p>{@code maPoints} 里每个点的 {@code ma} 都可能为 null，两种原因：
 *
 * <ol>
 *   <li><b>该点的窗口太稀疏</b>——窗口内日均值点少于 2 个，
 *       「平均」压不掉任何噪声，画出来比没有更误导</li>
 *   <li><b>整条序列点数不足</b>（{@code AC-7-2}：不足 3 天不显示移动平均线）——
 *       这时**所有** {@code ma} 都是 null</li>
 * </ol>
 *
 * <p>第 2 种情况服务端已经抹平了。为什么要额外做这一步：
 * {@code MIN_POINTS_IN_WINDOW = 2} 意味着只有两天数据时第二天的 {@code ma}
 * **是算得出来的**。如果不抹，就会出现「{@code enoughDataForMa = false}，
 * 但 {@code maPoints} 里躺着非 null 的 {@code ma}」——
 * 客户端只要漏看那个布尔量，就会画出一条 {@code METRICS 1.5} 明说不该画的线。
 *
 * <p>所以：<b>{@code ma} 是不是 null 决定画不画，{@code enoughDataForMa}
 * 只决定要不要显示「数据不足」那句提示。</b>一个是数据约束，一个是界面文案，
 * 各管各的，不会互相矛盾。
 *
 * <p>还有一条渲染规则留在客户端：{@code METRICS 1.5}「连续 14 天无数据 →
 * 曲线断开」。它**不是口径规则而是渲染规则**——不可能产出一个不同的数字，
 * 所以不需要服务端算（先例：{@code AC-7-11} 的「Y 轴不从 0 开始」）。
 *
 * <h3>⚠️ 序列化陷阱</h3>
 *
 * <p>{@code application.yml} 配了 {@code spring.jackson.default-property-inclusion: non_null}，
 * 而它管的是 **value + content 两者**——content inclusion 会作用于**集合元素**。
 *
 * <p>所以这里的 {@code ma} 是**对象字段**而不是「一个 {@code List<BigDecimal>} 里的 null 元素」。
 * 后者会被静默丢掉：{@code [1.0, null, 2.0]} 序列化成 {@code [1.0, 2.0]}，
 * 点位整体左移、X 轴全错，而且不报任何错。
 * （这个坑在 4A 就确认过，当时的结论就是「点位一律用对象」）
 */
public record BodySeriesResponse(

        String metricType,
        String metricLabel,
        String unit,

        /** 当前序列的部位。没有部位概念的指标是 {@code NONE} */
        String site,
        String siteLabel,

        /**
         * 有数据的部位（带中文名），按解剖学顺序。
         *
         * <p>{@code METRICS 2.5}：只测了部分部位时只显示有数据的部位，
         * 不显示空图。客户端用它画切换器——**不是**把 12 个部位全列出来。
         *
         * <p>非围度指标为空数组。
         */
        List<BodyMetricTypeResponse.SiteOption> availableSites,

        List<RawPoint> rawPoints,
        List<DailyPoint> dailyPoints,
        List<MaPoint> maPoints,

        /**
         * 数据够不够画移动平均线（{@code AC-7-2}：数据点不足 3 天 → 不显示 MA 线）。
         *
         * <p>由 {@code MovingAverage.enoughDataForMa} 和生成 MA 的**同一份规则**推导，
         * 不另写判断——否则会出现「画了线却提示数据不足」这种自相矛盾。
         *
         * <p><b>它只决定要不要显示「数据不足」那句提示，不决定画不画线。</b>
         * 画不画线看每个点的 {@code ma} 是不是 null——见类注释。
         * 这个分工是刻意的：{@code false} 时服务端已经把 {@code ma} 全抹成 null 了，
         * 所以「漏看这个字段」不会再导致画出一条不该画的线。
         */
        boolean enoughDataForMa,

        /** 最新一次测量。没有数据时为 null */
        LocalDateTime latestAt,
        BigDecimal latestValue,

        /**
         * 最新值相对参考基准的变化，以及基准叫什么。
         *
         * <p><b>⚠️ 不要把它理解成「距上一次测量」。</b>
         * 第一版就是那么写的，然后在一份真实的体重数据上算出了**反的**：
         * 体重 74.45 → 73.33（降了），接口却返回 {@code +1.01}——
         * 因为它是拿晨起空腹值去减训练后值，差值主要来自测量时机。
         *
         * <p>基准按优先级取：<b>7 日均值</b>（{@code METRICS 1.4} 明确要求显示
         * 「相对 7 日均值的偏差」）→ <b>上一次同条件的测量</b> → 无。
         *
         * <p>{@code referenceLabel} 就是给客户端显示用的（「7 日均值」/「上次晨起空腹」）。
         * 客户端**不要自己判断基准是什么**——那是服务端算的，两边判断会分叉。
         */
        ChangeVsReference changeVsReference,

        /** 测量条件不同时的提示文案（如「含训练后测量，读数会偏低」）。没有混杂时为 null */
        String conditionHint

) {

    /**
     * 空序列 —— 没有数据，或者**调用方还没选部位**。
     *
     * <p>后一种情况是 {@code GET /body/series?metricType=CIRCUMFERENCE}
     * 不带 {@code site} 时的返回值，见 {@code BodyService.series} 的注释：
     * 那不是错误，客户端此时恰恰需要 {@code availableSites} 才能选。
     */
    public static BodySeriesResponse empty(BodyMetricType type, BodySite site,
                                           List<BodyMetricTypeResponse.SiteOption> availableSites) {
        return new BodySeriesResponse(
                type.name(), type.getLabel(), type.getUnit(),
                site.name(), site.getLabel(),
                availableSites,
                List.of(), List.of(), List.of(),
                false, null, null,
                new ChangeVsReference(null, null),
                null);
    }

    /**
     * 相对参考基准的变化。
     *
     * @param value 变化量。**没有可用基准时为 null，不是 0**——
     *              「第一次记录」和「和上次一样」在界面上必须能区分
     * @param referenceLabel 基准的名称，直接显示给用户。没有基准时为 null
     */
    public record ChangeVsReference(BigDecimal value, String referenceLabel) {
    }

    public record RawPoint(
            LocalDateTime measuredAt,
            BigDecimal value,
            /** 可能为 null —— 该指标不支持条件，或用户没填 */
            String condition,
            String conditionLabel
    ) {
    }

    /** 一天一个点。{@code date} 在列表中唯一且升序 */
    public record DailyPoint(LocalDate date, BigDecimal value) {
    }

    /**
     * 移动平均点。{@code ma} 为 null 表示**该点没有可用的移动平均**
     * （窗口内点不足），客户端要断线，不是当成 0。
     */
    public record MaPoint(LocalDate date, BigDecimal value, BigDecimal ma) {
    }
}
