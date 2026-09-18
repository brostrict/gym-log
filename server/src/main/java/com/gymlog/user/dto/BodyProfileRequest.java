package com.gymlog.user.dto;

import java.math.BigDecimal;

/**
 * 更新「用于身体数据推导」的三个资料字段。
 *
 * <p>都是可空的——传 null 表示**不改这一项**，不是「清空」。
 * 用户只想改身高时不必把性别和出生年再传一遍，
 * 也避免「漏传 = 清空」这种危险的默认语义。
 *
 * <p>校验在 Service 层而不是这里：范围依赖「当前年份」（出生年），
 * 注解是编译期常量，表达不了。
 */
public record BodyProfileRequest(
        Integer gender,
        Integer birthYear,
        BigDecimal heightCm
) {
}
