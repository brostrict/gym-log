package com.gymlog.program.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新计划基本信息。
 *
 * <p><b>⚠️ 只有名称和说明，没有结构</b>（周、训练日、动作）。
 *
 * <p>结构变更走**版本化**路径（步骤 2.14）：归档旧版本 + 新建新版本。
 * 直接改结构会让已完成的训练记录「追溯性地改变含义」——
 * 用户第 5 周改了训练日的动作，前 4 周的历史会显示成改后的样子。
 *
 * <p>这是 REQUIREMENTS 6.3「不变量 2」的直接体现。
 */
public record ProgramUpdateRequest(

        @Size(max = 64, message = "计划名称不能超过 64 个字符")
        String name,

        @Size(max = 500, message = "计划说明不能超过 500 个字符")
        String description

) {
}
