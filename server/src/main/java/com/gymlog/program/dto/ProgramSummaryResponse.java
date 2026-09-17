package com.gymlog.program.dto;

import com.gymlog.program.Program;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 计划摘要 —— 用于计划列表。
 *
 * <p><b>列表不返回完整结构</b>：一个 8 周计划有上百条记录，
 * 列出 10 个计划就要传上千条数据。列表只需要「够用户认出是哪个计划」的信息。
 */
public record ProgramSummaryResponse(

        Long id,
        String name,
        String description,

        /** 总周数。0 = 不限期 */
        Integer totalWeeks,

        LocalDate startDate,
        LocalDate endDate,

        String status,
        String statusLabel,

        /** 版本号。前端可据此显示「v2」标识 */
        Integer version,

        /** 来源模板编码。NULL = 完全自定义 */
        String templateCode,

        /** 训练日数量（不含休息日）。列表页展示「3 个训练日」比让用户点进去看更友好 */
        int trainingDayCount,

        LocalDateTime createdAt

) {

    /**
     * @param trainingDayCount 需要额外查询，所以作为参数传入而非从实体推导
     */
    public static ProgramSummaryResponse from(Program p, int trainingDayCount) {
        return new ProgramSummaryResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getTotalWeeks(),
                p.getStartDate(),
                p.getEndDate(),
                p.getStatus() == null ? null : p.getStatus().name(),
                p.getStatus() == null ? null : p.getStatus().getDisplayName(),
                p.getVersion(),
                p.getTemplateCode(),
                trainingDayCount,
                p.getCreatedAt()
        );
    }
}
