package com.gymlog.body.dto;

import java.math.BigDecimal;

/**
 * 一个推导指标 —— 值 + 解读 + **算它缺什么**。
 *
 * <h3>为什么每条都要带 {@code missing}</h3>
 *
 * <p>推导需要「体重 + 身高 + 出生年 + 性别」。用户很可能只填了一部分
 * （比如填了身高但没填性别），这时正确的做法**不是**把整条藏起来，
 * 而是显示「体脂率估算 —— 需要先填性别」。
 *
 * <p>藏起来的话，用户根本不知道有这个指标，也就不会去补资料；
 * 而提示缺什么，这一条自己就变成了补资料的入口。
 *
 * <p>{@code value} 和 {@code missing} 互斥：有值就没有 missing，反之亦然。
 */
public record DerivedMetricResponse(

        /** 枚举名，如 {@code BMI} */
        String metricType,

        /** 中文名 */
        String label,

        String unit,

        /** 推导出的值。资料不全时为 {@code null} */
        BigDecimal value,

        /** 解读（「正常」/「超重」/「健康」）。没有值时为 {@code null} */
        String level,

        /**
         * 分级参考文案，直接显示。
         *
         * <p>例如 BMI 的「中国标准：18.5–23.9 正常」。
         * 放在服务端而不是客户端：分级标准会变（各国不同、会修订），
         * 而它是**和算值同一份知识**——分开写就会出现
         * 「算出来 23.5 却标成超重」这种自相矛盾。
         */
        String rangeHint,

        /**
         * 一句话说明这个数是怎么来的。
         *
         * <p>**这不是装饰**：V16 去掉体脂秤五项的理由之一就是黑箱。
         * 自己算的如果不写公式，和秤推的没有区别。
         */
        String formula,

        /** 缺哪些资料才能算出来。可以直接显示，如「身高、性别」 */
        String missing

) {
}
