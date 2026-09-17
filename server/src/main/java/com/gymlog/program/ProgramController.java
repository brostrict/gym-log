package com.gymlog.program;

import com.gymlog.common.PageResponse;
import com.gymlog.common.Result;
import com.gymlog.program.dto.ExpandedWorkout;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.program.dto.ProgramDetailResponse;
import com.gymlog.program.dto.ProgramFromTemplateRequest;
import com.gymlog.program.dto.ProgramStructureRequest;
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
    private final ExpansionService expansionService;

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
     * 从内置模板创建计划。
     *
     * <p>请求示例：
     * <pre>
     *   POST /api/v1/programs/from-template
     *   { "templateCode": "PPL_3DAY", "startDate": "2026-09-21" }
     * </pre>
     *
     * <p>创建出来的计划是**模板的一份完整拷贝**——之后用户改它，
     * 不会影响模板，也不会影响别人从同一模板创建的计划。
     *
     * <p><b>为什么路径是 {@code /from-template} 而不是
     * {@code POST /programs?templateCode=xxx}</b>：
     * 两者的请求体结构完全不同（一个是完整嵌套结构，一个只有三个字段）。
     * 用同一个路径靠参数区分，会让接口语义模糊，
     * 且 Swagger 上也无法清晰展示两种不同的 body。
     */
    @PostMapping("/from-template")
    public Result<Long> createFromTemplate(@AuthenticationPrincipal Long userId,
                                           @Valid @RequestBody ProgramFromTemplateRequest request) {
        return Result.ok(programService.createFromTemplate(userId, request));
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
     * 展开「第 N 周第 M 天具体练什么」。
     *
     * <p>这是**周期化展开算法**的出口：把「第 5 周 +5%」这样的修饰，
     * 展开成每一组的具体目标（重量、次数、休息）。
     *
     * <p>请求示例：
     * <pre>
     *   GET /api/v1/programs/12/workouts/1?week=5
     * </pre>
     *
     * <p><b>为什么 {@code week} 是可选的</b>：
     * 不传表示「按基准值展开」，用于用户在计划编辑页预览
     * 「这个训练日大致是什么样」——他此时不关心第几周。
     *
     * <p><b>为什么 day 放在路径里而 week 放在查询参数里</b>：
     * 训练日是**资源的固有组成部分**（一个训练日就是一个资源），
     * 而周是一个**筛选维度**——同一份训练日结构，第 5 周和第 6 周都适用。
     * 路径表达层级归属，查询参数表达筛选，这是 REST 的通用约定。
     */
    @GetMapping("/{id}/workouts/{dayNumber}")
    public Result<ExpandedWorkout> expandWorkout(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id,
                                                 @PathVariable Integer dayNumber,
                                                 @RequestParam(required = false) Integer week) {
        return Result.ok(expansionService.expandDay(userId, id, week, dayNumber));
    }

    /**
     * 更新计划的基本信息（名称、说明）。
     *
     * <p>用 PUT 而非 PATCH：整体替换语义，没传的字段会置空。
     *
     * <p><b>只改元信息，不动结构</b>——结构编辑走
     * {@code PUT /programs/{id}/structure}。拆开的理由见
     * {@code ProgramService.updateMeta} 的注释。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@AuthenticationPrincipal Long userId,
                               @PathVariable Long id,
                               @Valid @RequestBody ProgramUpdateRequest request) {
        programService.updateMeta(userId, id, request.name(), request.description());
        return Result.ok();
    }

    /**
     * 编辑计划结构 —— **全量替换**周与训练日。
     *
     * <p>请求示例：
     * <pre>
     *   PUT /api/v1/programs/12/structure
     *   {
     *     "expectedVersion": 3,
     *     "weeks": [ { "weekNumber": 1, "weightAdjustPct": 0, "setAdjust": 0 } ],
     *     "days":  [ { "dayNumber": 1, "name": "推日", "exercises": [ ... ] } ]
     *   }
     * </pre>
     *
     * <p><b>⚠️ 提交的是完整结构，不是增量。</b>
     * 没出现在请求里的训练日会被删除。
     * 客户端应把当前详情页的完整状态提交回来。
     *
     * <p><b>返回完整的新结构</b>（而不只是 204）：
     * 全量替换后所有子记录的 id 都变了，客户端手里那份缓存已经失效。
     * 返回体里也带着新的 {@code version}，用户可以连续编辑而不用重新 GET。
     *
     * <p><b>{@code expectedVersion} 必填</b>，用于乐观锁。
     * 对不上返回 40003。详见 {@code ProgramStructureRequest}。
     */
    @PutMapping("/{id}/structure")
    public Result<ProgramDetailResponse> updateStructure(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @Valid @RequestBody ProgramStructureRequest request) {
        return Result.ok(programService.updateStructure(userId, id, request));
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
