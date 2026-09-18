package com.gymlog.training.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 训练历史列表的一项。
 *
 * <p>对应 M5「历史列表与日历」。
 *
 * <p><b>只带列表需要的字段</b>：日期、训练日名、时长、容量、组数。
 * 完整内容（每个动作每一组）在详情接口里，
 * 列表一次拉 20 条，每条再带完整内容的话响应会膨胀几十倍。
 */
public record SessionListItem(

        Long id,

        /** 训练日期。由 {@code startedAt} 取日期部分，用于日历分组 */
        LocalDate date,

        LocalDateTime startedAt,

        /** 快照的训练日名。null = 临时训练 */
        String dayName,

        Integer weekNumber,
        boolean deload,

        String status,
        String statusLabel,

        Integer durationSec,

        /** 训练容量（kg）。口径见 METRICS 4.1 */
        BigDecimal volume,

        /** 正式组数（不含热身） */
        int workingSets,

        /** 动作数量，用于列表里显示「5 个动作」 */
        int exerciseCount
) {
}
