package com.gymlog.body.dto;

import com.gymlog.body.BodyMetricType;
import com.gymlog.body.BodySite;
import com.gymlog.body.MetricCondition;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记录一次身体测量。
 *
 * <h3>这是本项目第一个没有「业务键」可用的写接口</h3>
 *
 * <p>前两个写接口的幂等键都来自数据本身：
 * <pre>
 *   workout_session   client_key（客户端生成的 UUID）
 *   set_record        (session_exercise_id, set_number) —— 组号本身就是标识
 * </pre>
 *
 * <p>身体数据也有自己的业务键：<b>(指标, 部位, 测量时刻)</b>。
 * 同一个人不可能在同一时刻有两个体重。所以唯一键
 * {@code uk_body_metric_natural} 就是它，不另加 {@code client_key}——
 * 和 {@code set_record} 的理由一样：**客户端重试时那个 UUID 可能被重新生成**，
 * 而业务键不会。
 *
 * <h3>为什么不在 DTO 上做量程校验</h3>
 *
 * <p>量程是**按指标不同**的（体重 20–400，睡眠质量 1–5），
 * 注解是编译期常量，表达不了。所以这里只做「非空」这类与指标无关的校验，
 * 量程交给 {@link BodyMetricType#validateValue}——那里是量程的唯一定义处。
 *
 * <p>用 {@code @DecimalMin} 之类的注解再写一遍的话，
 * 改量程要改两个地方，而漏改的那次不会报错。
 */
public record BodyMetricRequest(

        /** 指标类型。未知值会在 JSON 反序列化阶段就被拒（枚举绑定失败） */
        @NotNull(message = "缺少指标类型")
        BodyMetricType metricType,

        /**
         * 部位。
         *
         * <p>不传 = {@link BodySite#NONE}（没有部位概念的指标）。
         * 传了但该指标不支持 → 60006；该指标必须传却没传 → 60005。
         */
        BodySite site,

        @NotNull(message = "缺少数值")
        BigDecimal value,

        /**
         * 测量时刻。
         *
         * <p><b>必填，由客户端提供</b>——离线记录时服务端不在场。
         * 用服务端时间的话，早上称的体重会被记成晚上提交的时刻，
         * 而「晨起空腹」这个条件就白填了。
         */
        @NotNull(message = "缺少测量时刻")
        LocalDateTime measuredAt,

        /** 测量条件。该指标不支持时忽略（老客户端多传一个字段不该让用户录不进去） */
        MetricCondition condition,

        @Size(max = 255)
        String note

) {
}
