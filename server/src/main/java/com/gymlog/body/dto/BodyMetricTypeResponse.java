package com.gymlog.body.dto;

import com.gymlog.body.BodyMetricType;
import com.gymlog.body.BodySite;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一个身体指标的元数据 —— 客户端据此生成录入表单。
 *
 * <h3>为什么要有这个接口（{@code GET /body/metric-types}）</h3>
 *
 * <p>客户端需要知道：叫什么、单位是什么、数值填多少算合理、
 * 要不要选部位、要不要填测量条件和设备型号。
 *
 * <p><b>而服务端也必须知道同一批知识</b>——量程校验要用它
 * （{@code BODY_METRIC_VALUE_OUT_OF_RANGE} 60002）。
 *
 * <p>如果客户端硬编码一份，就是同一个知识两头写，而且失效方式很不友好：
 * 服务端把体重上限从 300 改成 400，客户端输入框还卡在 300，
 * 用户填 350 被前端拦住、请求根本发不出去，而后端其实是接受的；
 * 反过来则是客户端放行、后端拒绝，用户看到一句看不懂的报错。
 *
 * <p>所以这里是 {@link BodyMetricType} 的**直译**，没有任何加工。
 */
public record BodyMetricTypeResponse(

        /** 枚举名，录入时回传 */
        String metricType,

        /** 中文名，直接显示 */
        String label,

        /** 单位。不存库——由 metricType 唯一决定，避免「kg / 公斤」两种写法 */
        String unit,

        /** 分组（身体测量 / 体成分 / 生理指标 / 主观状态），客户端按它分节 */
        String group,
        String groupLabel,

        /** 合理量程，含端点。客户端用它设输入框范围，服务端用它校验 */
        BigDecimal min,
        BigDecimal max,

        /**
         * 有没有部位概念。
         *
         * <p>{@code false} → 客户端**不显示**部位选择器。
         * 注意这和 {@code sites} 为空是同一件事，保留这个字段只是为了
         * 让客户端的判断读起来是一句人话。
         */
        boolean hasSites,

        /** 必须选部位吗。围度是 true（「围度 100cm」不说明任何事） */
        boolean siteRequired,

        /** 允许的部位（带中文名）。{@code hasSites = false} 时为空数组 */
        List<SiteOption> sites,

        /** 要不要显示「测量条件」。体重、静息心率这类对条件敏感的指标为 true */
        boolean conditionSupported

) {

    /** 一个部位选项 */
    public record SiteOption(
            String site,
            String label,
            /** 是不是四肢（左右成对）。只有成对的部位才谈得上「不对称度」（METRICS 2.2） */
            boolean paired
    ) {
    }

    public static BodyMetricTypeResponse from(BodyMetricType type) {
        List<SiteOption> sites = type.hasSites()
                ? type.getAllowedSites().stream()
                        .map(s -> new SiteOption(s.name(), s.getLabel(), s.isPaired()))
                        .toList()
                : List.of();

        return new BodyMetricTypeResponse(
                type.name(),
                type.getLabel(),
                type.getUnit(),
                type.getGroup().name(),
                type.getGroup().getLabel(),
                type.getMin(),
                type.getMax(),
                type.hasSites(),
                type.requiresSite(),
                sites,
                type.isConditionSupported());
    }

    /** 所有可用于 {@code site} 字段的枚举值，附带它属于哪个指标的哪个标签 */
    public static SiteOption siteOption(BodySite site) {
        return new SiteOption(site.name(), site.getLabel(), site.isPaired());
    }
}
