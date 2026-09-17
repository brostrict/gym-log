package com.gymlog.program;

import com.gymlog.common.PageResponse;
import com.gymlog.common.Result;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.program.dto.ProgramDetailResponse;
import com.gymlog.program.dto.ProgramSummaryResponse;
import com.gymlog.program.dto.ProgramUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 训练计划接口。
 *
 * <p>所有接口都需要登录，且**只能操作自己的计划**——
 * 归属校验在 Service 层统一做（{@code loadOwned}）。
 */
@RestController
@RequestMapping("/api/v1/programs")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    /**
     * 创建计划（一次提交完整结构）。
     *
     * <p>请求体包含周、训练日、动作、逐组处方的完整嵌套结构。
     * 整组操作在一个事务里，任何一层失败全部回滚。
     */
    @PostMapping
    public Result<Long> create(@AuthenticationPrincipal Long userId,
                               @Valid @RequestBody ProgramCreateRequest request) {
        return Result.ok(programService.create(userId, request));
    }

    /**
     * 我的计划列表。
     *
     * <p>不返回完整结构——列表只需要「够认出是哪个计划」的信息。
     * 已归档的历史版本不出现在列表里。
     */
    @GetMapping
    public Result<PageResponse<ProgramSummaryResponse>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ProgramStatus status) {
        return Result.ok(PageResponse.from(programService.list(userId, page, size, status)));
    }

    /**
     * 计划详情（完整嵌套结构）。
     *
     * <p>这个接口会返回周、训练日、动作、逐组处方的全部数据，
     * 一次拿完，客户端不需要再补查询。
     */
    @GetMapping("/{id}")
    public Result<ProgramDetailResponse> detail(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long id) {
        return Result.ok(programService.detail(userId, id));
    }

    /**
     * 更新计划的基本信息（名称、说明）。
     *
     * <p>用 PUT 而非 PATCH：整体替换语义，没传的字段会置空。
     *
     * <p><b>不支持改结构</b>——结构变更走版本化路径（步骤 2.14）。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@AuthenticationPrincipal Long userId,
                               @PathVariable Long id,
                               @Valid @RequestBody ProgramUpdateRequest request) {
        programService.updateMeta(userId, id, request.name(), request.description());
        return Result.ok();
    }

    /**
     * 暂停 / 恢复计划。
     *
     * <p><b>为什么单独一个接口而不是放到 PUT 里</b>：
     * 状态变更是**动作**而非**数据编辑**——它有独立的业务规则
     * （暂停期间不计入完成率分母、归档不能手动设置）。
     * 混在通用更新接口里，规则就没地方放了。
     *
     * <p>用 PATCH：只改状态这一个字段，其他字段不受影响。
     */
    @PatchMapping("/{id}/status")
    public Result<Void> changeStatus(@AuthenticationPrincipal Long userId,
                                     @PathVariable Long id,
                                     @RequestParam ProgramStatus status) {
        programService.changeStatus(userId, id, status);
        return Result.ok();
    }

    /**
     * 删除计划（级联删除其下所有结构）。
     *
     * <p><b>⚠️ 待办</b>：步骤 2.14 后要加「有训练记录引用时不能删」的检查。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal Long userId,
                               @PathVariable Long id) {
        programService.delete(userId, id);
        return Result.ok();
    }
}
