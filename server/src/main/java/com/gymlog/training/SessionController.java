package com.gymlog.training;

import com.gymlog.common.Result;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SessionDetailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 训练会话接口。
 *
 * <p>所有接口都需要登录，且**只能操作自己的会话**——
 * 归属校验在 Service 层统一做（{@code loadOwned}）。
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    /**
     * 开始一次训练。
     *
     * <p>请求示例：
     * <pre>
     *   POST /api/v1/sessions
     *   { "programId": 12, "dayNumber": 1, "clientKey": "550e8400-..." }
     * </pre>
     *
     * <p><b>{@code clientKey} 建议必传</b>：离线重试靠它去重。
     * key 已存在时返回**已存在的那条会话**而不是报错——
     * 客户端重试本来就不该收到错误。
     *
     * <p><b>一个人同时只能练一场。</b>如果已经有一个进行中的会话，
     * 这个接口返回那一场并把 {@code resumed} 置为 true，
     * 而不是新建一条。客户端据此显示「继续上次训练」。
     */
    @PostMapping
    public Result<SessionDetailResponse> create(@AuthenticationPrincipal Long userId,
                                                @Valid @RequestBody SessionCreateRequest request) {
        return Result.ok(sessionService.create(userId, request));
    }

    /**
     * 当前进行中的训练。
     *
     * <p>App 启动时调用，用于断点续训：有未完成的会话就提示「继续训练」。
     *
     * <p>没有进行中的会话时返回 200 且 {@code data} 为 null——
     * 「我现在没在训练」是正常状态，不是错误。
     */
    @GetMapping("/active")
    public Result<SessionDetailResponse> active(@AuthenticationPrincipal Long userId) {
        WorkoutSession active = sessionService.findActive(userId);
        return Result.ok(active == null
                ? null
                : sessionService.detail(userId, active.getId(), true));
    }

    /**
     * 训练详情（含完整快照）。
     *
     * <p>返回的内容**全部来自快照**，不回查计划。
     * 用户改计划之后，这里返回的还是当时练的东西。
     */
    @GetMapping("/{id}")
    public Result<SessionDetailResponse> detail(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long id) {
        return Result.ok(sessionService.detail(userId, id, false));
    }

    /**
     * 结束训练。
     *
     * <p><b>只有完成才推进训练日轮转。</b>
     *
     * <p>{@code durationSec} 由客户端上报而不是服务端算
     * （{@code finishedAt - startedAt}），因为中途暂停了多久只有客户端知道。
     */
    @PatchMapping("/{id}/finish")
    public Result<SessionDetailResponse> finish(@AuthenticationPrincipal Long userId,
                                                @PathVariable Long id,
                                                @RequestParam(required = false) Integer durationSec,
                                                @RequestParam(required = false) String note) {
        return Result.ok(sessionService.finish(userId, id, durationSec, note));
    }

    /**
     * 放弃这次训练。
     *
     * <p>记录**保留**，只是不计入完成率和轮转。
     * 与「删除」的区别：放弃是「我练了但没练完」，删是「这条不该存在」。
     */
    @PatchMapping("/{id}/abandon")
    public Result<SessionDetailResponse> abandon(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long id) {
        return Result.ok(sessionService.abandon(userId, id));
    }
}
