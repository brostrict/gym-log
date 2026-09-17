package com.gymlog.program.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 从模板创建计划的请求。
 *
 * <p><b>为什么只有三个字段</b>：模板里已经有完整的周/日/动作结构，
 * 用户只需要决定「用哪个模板」「叫什么名字」「什么时候开始」。
 *
 * <p>如果用户想改结构（增删动作、调组数），那是**创建之后**的事——
 * 创建时先原样落库，再让他改。这样有两个好处：
 * <ol>
 *   <li>用户能看到模板的原始样子，知道自己在改什么</li>
 *   <li>创建逻辑不需要处理「部分覆盖模板」的复杂情况</li>
 * </ol>
 */
public record ProgramFromTemplateRequest(

        @NotBlank(message = "请选择计划模板")
        @Size(max = 64)
        String templateCode,

        /** 计划名称。不传则用模板名称 */
        @Size(max = 64, message = "计划名称不能超过 64 个字符")
        String name,

        LocalDate startDate

) {
}
