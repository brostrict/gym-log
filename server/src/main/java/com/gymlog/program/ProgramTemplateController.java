package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.Result;
import com.gymlog.program.dto.ProgramTemplateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 计划模板接口。
 *
 * <p>模板是**公共只读数据**——所有人都能看到，没有归属概念。
 * 所以这里的接口只读，增删改在管理端（Phase 6）。
 */
@RestController
@RequestMapping("/api/v1/program-templates")
@RequiredArgsConstructor
public class ProgramTemplateController {

    private final ProgramTemplateMapper templateMapper;

    /**
     * 模板列表。
     *
     * <p>只返回到上架的模板，按 {@code sortOrder} 排序。
     *
     * <p><b>为什么这个接口不分页</b>：内置模板是个位数（V1 是 6 个），
     * 且永远不会快速增长。分页只会增加客户端的复杂度。
     * 如果将来模板数量上百，再加分页。
     */
    @GetMapping
    public Result<List<ProgramTemplateResponse>> list() {
        List<ProgramTemplateResponse> templates = templateMapper.selectList(
                        new LambdaQueryWrapper<ProgramTemplate>()
                                .eq(ProgramTemplate::getStatus, ProgramTemplate.STATUS_PUBLISHED)
                                .orderByAsc(ProgramTemplate::getSortOrder))
                .stream()
                .map(ProgramTemplateResponse::from)
                .toList();
        return Result.ok(templates);
    }

    /**
     * 模板详情（不含 structure）。
     *
     * <p>结构本身不返回给客户端——用户在 App 上看到的应该是
     * 「这个模板练什么、每周几次、需要什么器械」，
     * 而不是一堆 JSON。
     *
     * <p>想看具体内容（比如「推日有哪些动作」），
     * 那是模板预览功能，需要时再单独做接口。
     */
    @GetMapping("/{code}")
    public Result<ProgramTemplateResponse> detail(@PathVariable String code) {
        ProgramTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<ProgramTemplate>()
                        .eq(ProgramTemplate::getCode, code)
                        .eq(ProgramTemplate::getStatus, ProgramTemplate.STATUS_PUBLISHED));

        if (template == null) {
            throw new com.gymlog.common.BizException(
                    com.gymlog.common.ErrorCode.NOT_FOUND, "计划模板不存在或已下架");
        }

        return Result.ok(ProgramTemplateResponse.from(template));
    }
}
