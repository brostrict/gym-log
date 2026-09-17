package com.gymlog.program.dto;

import com.gymlog.program.ProgramTemplate;

/**
 * 计划模板摘要（列表用）。
 *
 * <p><b>⚠️ 不包含 {@code structure} 字段</b>——那是完整的嵌套结构，
 * 一个模板上百条记录。列出 6 个模板就要传几千条数据，
 * 而列表页只需要「够用户判断要不要选它」的信息。
 *
 * <p>结构在用户点「使用这个模板」时才需要，那时直接调创建接口即可，
 * 客户端根本不需要拿到结构本身。
 */
public record ProgramTemplateResponse(

        String code,
        String name,
        String description,

        /** 适用目标：MUSCLE_GAIN / STRENGTH / FAT_LOSS / GENERAL */
        String goal,

        /** 适用水平：BEGINNER / INTERMEDIATE / ADVANCED */
        String level,

        Integer sessionsPerWeek,
        Integer totalWeeks,
        Integer daysPerWeek,

        String equipmentSummary,
        Integer estimatedMinutes

) {

    public static ProgramTemplateResponse from(ProgramTemplate t) {
        return new ProgramTemplateResponse(
                t.getCode(),
                t.getName(),
                t.getDescription(),
                t.getGoal(),
                t.getLevel(),
                t.getSessionsPerWeek(),
                t.getTotalWeeks(),
                t.getDaysPerWeek(),
                t.getEquipmentSummary(),
                t.getEstimatedMinutes()
        );
    }
}
