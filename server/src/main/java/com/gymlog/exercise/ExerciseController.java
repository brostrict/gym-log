package com.gymlog.exercise;

import com.gymlog.common.PageResponse;
import com.gymlog.common.QueryParamGuard;
import com.gymlog.common.Result;
import com.gymlog.exercise.dto.ExerciseQuery;
import com.gymlog.exercise.dto.ExerciseResponse;
import com.gymlog.exercise.dto.ExerciseSaveRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 动作库接口。
 *
 * <p>这些接口都需要登录——{@code /api/v1/exercises/**} 不在公开路径列表里。
 */
@RestController
@RequestMapping("/api/v1/exercises")
@RequiredArgsConstructor
public class ExerciseController {

    private final ExerciseService exerciseService;

    /**
     * 分页查询动作。
     *
     * <p>请求示例：
     * <pre>
     *   GET /api/v1/exercises                               全部（内置 + 我的）
     *   GET /api/v1/exercises?primaryMuscle=CHEST           只看胸
     *   GET /api/v1/exercises?equipment=DUMBBELL            只看哑铃动作
     *   GET /api/v1/exercises?movementPattern=HORIZONTAL_PUSH  找水平推的替代动作
     *   GET /api/v1/exercises?metricType=REPS_ONLY          只看徒手动作
     *   GET /api/v1/exercises?keyword=卧推                   搜索
     *   GET /api/v1/exercises?onlyCustom=true               只看我建的
     * </pre>
     *
     * <p><b>参数名与响应字段名一一对应</b>，可以照着响应里看到的字段名直接拼查询串。
     * 传了不认识的参数会**直接报错**，不会静默忽略（见 {@link #initBinder}）。
     *
     * <p><b>为什么用 {@code @AuthenticationPrincipal} 拿 userId</b>：
     * 查询结果要按用户过滤（只能看到内置 + 自己的自定义动作）。
     * userId 必须来自 token，**绝不能作为请求参数**——
     * 否则客户端传别人的 userId 就能看到别人的自定义动作。
     */
    @GetMapping
    public Result<PageResponse<ExerciseResponse>> list(@AuthenticationPrincipal Long userId,
                                                       ExerciseQuery query,
                                                       HttpServletRequest request) {
        // 必须放在最前面：参数名拼错时，Spring 会**静默忽略**它，
        // 于是后面拿到的是「没加筛选条件」的查询对象。
        // 先拦下来，后面的代码就不必怀疑自己的入参是不是被吃掉了。
        rejectUnknownParams(request);
        return Result.ok(PageResponse.from(exerciseService.query(userId, query)));
    }

    /**
     * 未知的查询参数**直接报错**，而不是被静默忽略。
     *
     * <p>背景与踩坑见 {@link QueryParamGuard}——那里解释了
     * 为什么不能用框架的 {@code setIgnoreUnknownFields(false)}
     * （会把 HTTP 请求头也当成待绑定属性，导致每个请求 500）。
     */
    private void rejectUnknownParams(HttpServletRequest request) {
        QueryParamGuard.rejectUnknown(
                ExerciseQuery.class, Collections.list(request.getParameterNames()));
    }

    /**
     * 查询单个动作详情。
     *
     * <p>⚠️ 注意这里**必须也做可见性校验**——不能只按 id 查。
     * 否则用户 A 传一个用户 B 的自定义动作 id，就能看到 B 的动作内容。
     *
     * <p>这类「按 id 直查」的接口是**越权漏洞最常见的入口**。
     * 单条查询和列表查询用的是两套代码路径，
     * 列表加了过滤条件、单条忘了加，是极常见的疏漏。
     *
     * <p>（本步骤先只加列表接口，单条查询在实现时按同样的规则处理。）
     */
    @GetMapping("/{id}")
    public Result<ExerciseResponse> detail(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long id) {
        return Result.ok(ExerciseResponse.from(
                exerciseService.getVisibleById(userId, id)
        ));
    }

    // ==================================================================
    // 自定义动作的增删改
    //
    // 注意：这些接口只能操作用户**自己创建**的动作。
    // 内置动作由系统维护，改动会影响所有用户，只能通过管理端修改（Phase 6）。
    // ==================================================================

    /**
     * 新建自定义动作。
     *
     * <p><b>返回 200 而不是 201</b>：项目统一用 {@code Result} 包装，
     * 成功即 200。用 201 会导致客户端要处理两种成功状态码，
     * 而收益只是「更符合 REST 语义」——统一性更重要。
     */
    @PostMapping
    public Result<Long> create(@AuthenticationPrincipal Long userId,
                               @Valid @RequestBody ExerciseSaveRequest request) {
        return Result.ok(exerciseService.create(userId, request));
    }

    /**
     * 编辑自定义动作。
     *
     * <p>用 PUT 而不是 PATCH：这个接口是**整体替换**语义——
     * 请求里没带的字段会被置空，而不是保留原值。
     * 客户端必须提交完整对象。
     */
    @PutMapping("/{id}")
    public Result<Void> update(@AuthenticationPrincipal Long userId,
                               @PathVariable Long id,
                               @Valid @RequestBody ExerciseSaveRequest request) {
        exerciseService.update(userId, id, request);
        return Result.ok();
    }

    /**
     * 删除自定义动作（逻辑删除）。
     *
     * <p><b>⚠️ 待办</b>：步骤 2.8 计划 CRUD 完成后，这里要补「引用检查」——
     * 被计划引用的动作不能删，应返回 {@code EXERCISE_IN_USE}。
     * 详见 {@code ExerciseService.delete} 的注释。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@AuthenticationPrincipal Long userId,
                               @PathVariable Long id) {
        exerciseService.delete(userId, id);
        return Result.ok();
    }
}
