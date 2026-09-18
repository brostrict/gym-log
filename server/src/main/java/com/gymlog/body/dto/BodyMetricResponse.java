package com.gymlog.body.dto;

import com.gymlog.body.BodyMetric;
import com.gymlog.body.BodyMetricType;
import com.gymlog.body.BodySite;
import com.gymlog.body.MetricCondition;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一条身体数据记录。
 *
 * <p>中文名和单位都从 {@link BodyMetricType} 现取，**不存库也不硬编码**——
 * 客户端拿到的永远和服务端校验用的是同一份定义。
 */
public record BodyMetricResponse(

        Long id,

        String metricType,
        String metricLabel,
        String unit,

        /** 没有部位概念的指标是 {@code NONE}，客户端据此决定显不显示部位 */
        String site,
        String siteLabel,

        BigDecimal value,

        LocalDateTime measuredAt,

        /** 可能为 null——该指标不支持条件是正常的，不是缺数据 */
        MetricCondition condition,
        String conditionLabel,

        String note

) {

    public static BodyMetricResponse from(BodyMetric m) {
        BodyMetricType type = m.getMetricType();
        BodySite site = m.getSite();
        MetricCondition condition = m.getMeasureCondition();

        return new BodyMetricResponse(
                m.getId(),
                type.name(),
                type.getLabel(),
                type.getUnit(),
                site.name(),
                site.getLabel(),
                m.getValue(),
                m.getMeasuredAt(),
                condition,
                condition == null ? null : condition.getLabel(),
                m.getNote());
    }
}
