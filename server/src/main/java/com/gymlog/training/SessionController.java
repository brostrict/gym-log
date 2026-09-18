package com.gymlog.training;

import com.gymlog.common.PageResponse;
import com.gymlog.common.Result;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SessionDetailResponse;
import com.gymlog.training.dto.SessionListItem;
import com.gymlog.training.dto.SessionSummaryResponse;
import com.gymlog.training.dto.SetRecordRequest;
import com.gymlog.training.dto.SetRecordResponse;
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
    private final SetRecordService setRecordService;
    private final SessionSummaryService summaryService;

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
     * 训练历史（分页，按时间倒序）。
     *
     * <p>对应 M5「历史列表与日历」。每项只带列表需要的字段——
     * 完整内容（每个动作每一组）在详情接口里。
     */
    @GetMapping
    public Result<PageResponse<SessionListItem>> history(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(PageResponse.from(summaryService.history(userId, page, size)));
    }

    /**
     * 训练总结 —— 练完之后那一屏。
     *
     * <p>包含：容量、组数、时长、完成度、本次刷新的 PR、与上一次同训练日的对比。
     *
     * <p><b>容量和组数都给</b>，因为它们回答不同的问题（METRICS 4.0）：
     * 容量看「总负荷涨没涨」，组数看「练得够不够」。
     * 只给一个会让用户做出错误的训练决策。
     */
    @GetMapping("/{id}/summary")
    public Result<SessionSummaryResponse> summary(@AuthenticationPrincipal Long userId,
                                                  @PathVariable Long id) {
        return Result.ok(summaryService.summary(userId, id));
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
     * <p><b>训练时长由服务端推算</b>：最后一组的完成时刻 − 会话开始时刻。
     * 不用「点结束的时刻」——用户练完常常不会马上点，
     * 两小时后才想起来的话那两小时会算进训练时长里。
     *
     * <p>{@code durationSec} 参数只在**该会话一条组记录都没有**时作兜底。
     * 正常训练不需要传。
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

    // ==================================================================
    // 组记录
    // ==================================================================

    /**
     * 记录 / 覆盖一组。
     *
     * <p>请求示例：
     * <pre>
     *   PUT /api/v1/sessions/96/exercises/161/sets/3
     *   { "weight": 70, "reps": 5, "rpe": 8, "restActualSec": 148,
     *     "completedAt": "2026-09-21T19:42:00" }
     * </pre>
     *
     * <h3>为什么是 PUT 而不是 POST</h3>
     *
     * <p>路径 {@code .../sets/3} 已经唯一确定了「哪个动作的第几组」，
     * 所以这是**幂等**的：同样的请求发几次，结果都一样。
     * PUT 的语义天然如此，而 POST 意味着「每次都会新建一条」——
     * 那正是离线重试会产生重复记录的原因。
     *
     * <p>唯一索引 {@code (session_exercise_id, set_number)} 是这条幂等性的
     * 数据库层保障，接口语义只是把它表达出来。
     *
     * <p><b>返回的是这个动作的进度，不是整份会话</b>——
     * 跟练时每 2-3 分钟记一组，每次都拉整份会话没必要。
     */
    @PutMapping("/{id}/exercises/{sessionExerciseId}/sets/{setNumber}")
    public Result<SetRecordResponse> recordSet(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long sessionExerciseId,
            @PathVariable Integer setNumber,
            @Valid @RequestBody SetRecordRequest request) {
        return Result.ok(setRecordService.recordSet(
                userId, id, sessionExerciseId, setNumber, request));
    }

    /**
     * 删除一组（M4-D-3：临时删除组）。
     *
     * <p><b>只影响本次会话</b>，不碰计划模板（不变量 3）——
     * 删掉的那一组，下次练同样的计划还是会有。
     *
     * <p>M4-D-5 要求客户端做二次确认；服务端这里做幂等——
     * 删一个本来就不存在的组不报错，因为离线队列里
     * 「删除」和「记录」可能乱序到达，报错会让客户端卡在重试上。
     */
    @DeleteMapping("/{id}/exercises/{sessionExerciseId}/sets/{setNumber}")
    public Result<SetRecordResponse> deleteSet(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long sessionExerciseId,
            @PathVariable Integer setNumber) {
        return Result.ok(setRecordService.deleteSet(userId, id, sessionExerciseId, setNumber));
    }

    /**
     * 改变动作状态（M4-D-1 跳过当前动作）。
     *
     * <p>和「记录组」的自动推进不同，这是用户的**明确决定**，
     * 不该被「记录了几组」覆盖掉。
     */
    @PatchMapping("/{id}/exercises/{sessionExerciseId}/status")
    public Result<SetRecordResponse> updateExerciseStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long id,
            @PathVariable Long sessionExerciseId,
            @RequestParam SessionExerciseStatus status) {
        return Result.ok(setRecordService.updateExerciseStatus(userId, id, sessionExerciseId, status));
    }
}
