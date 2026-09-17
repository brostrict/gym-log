package com.gymlog.exercise;

import com.gymlog.common.PageResponse;
import com.gymlog.common.Result;
import com.gymlog.exercise.dto.ExerciseQuery;
import com.gymlog.exercise.dto.ExerciseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     *   GET /api/v1/exercises                         全部（内置 + 我的）
     *   GET /api/v1/exercises?muscle=CHEST            只看胸
     *   GET /api/v1/exercises?equipment=DUMBBELL      只看哑铃动作
     *   GET /api/v1/exercises?pattern=HORIZONTAL_PUSH 找水平推的替代动作
     *   GET /api/v1/exercises?metricType=REPS_ONLY    只看徒手动作
     *   GET /api/v1/exercises?keyword=卧推             搜索
     *   GET /api/v1/exercises?onlyCustom=true         只看我建的
     * </pre>
     *
     * <p><b>为什么用 {@code @AuthenticationPrincipal} 拿 userId</b>：
     * 查询结果要按用户过滤（只能看到内置 + 自己的自定义动作）。
     * userId 必须来自 token，**绝不能作为请求参数**——
     * 否则客户端传别人的 userId 就能看到别人的自定义动作。
     */
    @GetMapping
    public Result<PageResponse<ExerciseResponse>> list(@AuthenticationPrincipal Long userId,
                                                       ExerciseQuery query) {
        return Result.ok(PageResponse.from(exerciseService.query(userId, query)));
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
}
