package com.gymlog.training.dto;

import com.gymlog.training.SetType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 记录一组。
 *
 * <p><b>不需要传动作或会话 id</b>——它们都在 URL 路径里。
 * 请求体只描述「这一组做了什么」。
 *
 * <h3>哪些字段有值由动作的计量类型决定</h3>
 * <pre>
 *   WEIGHT_REPS        weight + reps
 *   REPS_ONLY          reps
 *   DURATION           durationSec
 *   DISTANCE_DURATION  durationSec + distanceM
 * </pre>
 *
 * <p>服务端**不强制校验这个匹配**。理由：计划里是卧推，用户临时改成做俯卧撑，
 * 那时候只填次数就是对的。硬校验会把临场调整卡死，
 * 而临场调整是跟练功能的 Must（M4-D）。
 *
 * <p>改成由客户端按 {@code metricType} 决定显示哪些输入框——
 * 它知道该显示什么，服务端只负责存下来。
 */
public record SetRecordRequest(

        /** 组类型。默认正式组 */
        SetType setType,

        @DecimalMin(value = "0.0", message = "重量不能为负")
        @DecimalMax(value = "1000.0", message = "重量超出合理范围")
        BigDecimal weight,

        @Min(value = 0, message = "次数不能为负")
        @Max(value = 1000, message = "次数超出合理范围")
        Integer reps,

        @Min(value = 0, message = "时长不能为负")
        @Max(value = 86400, message = "时长超出合理范围")
        Integer durationSec,

        @DecimalMin(value = "0.0", message = "距离不能为负")
        @DecimalMax(value = "100000.0", message = "距离超出合理范围")
        BigDecimal distanceM,

        /** 实际 RPE。可选（M4-C-5）——用户可以只记重量次数不记 RPE */
        @DecimalMin(value = "1.0", message = "RPE 最小为 1")
        @DecimalMax(value = "10.0", message = "RPE 最大为 10")
        BigDecimal rpe,

        /**
         * 本组之后的**实际**休息秒数。
         *
         * <p>M4-C-7 要求自动记录。这个值只有客户端知道——
         * 服务端不知道用户什么时候放下手机又拿起来。
         *
         * <p>最后一组之后没有休息，不传即可。
         */
        @Min(value = 0, message = "休息时长不能为负")
        @Max(value = 3600, message = "休息时长超出合理范围")
        Integer restActualSec,

        @Size(max = 255)
        String note,

        /**
         * 完成时刻。
         *
         * <p><b>必填，由客户端提供</b>：离线训练时服务端不在场，
         * 收到这条记录可能已经是几小时后了。用服务端时间会让
         * 「晚上 7 点练的」变成「凌晨 1 点练的」。
         */
        @NotNull(message = "缺少完成时刻")
        LocalDateTime completedAt

) {
}
