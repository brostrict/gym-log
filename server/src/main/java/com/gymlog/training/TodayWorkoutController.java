package com.gymlog.training;

import com.gymlog.common.Result;
import com.gymlog.training.dto.TodayWorkoutResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 「今天练什么」。
 *
 * <p>这是 App 首页加载时调的第一个接口——M4-A-1 的今日训练卡片。
 */
@RestController
@RequestMapping("/api/v1/workouts")
@RequiredArgsConstructor
public class TodayWorkoutController {

    private final TodayWorkoutService todayWorkoutService;

    /**
     * 今天练什么。
     *
     * <p>请求示例：
     * <pre>
     *   GET /api/v1/workouts/today
     *   GET /api/v1/workouts/today?date=2026-09-21
     *   GET /api/v1/workouts/today?day=2          ← 切换到第 2 个训练日（M4-A-4）
     *   GET /api/v1/workouts/today?programId=12
     * </pre>
     *
     * <p><b>没有进行中的计划时返回 200 且 {@code programId} 为 null</b>，
     * 不是 404——「我没有计划」是正常状态，不是错误。
     * 用 404 会让客户端把空状态和真正的请求失败混在一起处理。
     *
     * <p><b>{@code date} 参数是为离线场景准备的</b>：
     * 手机端可能缓存了几天的计划，或者用户想「回看周三该练什么」。
     * 不传就是今天。
     *
     * <p><b>为什么用查询参数而不是路径</b>：
     * 「今天」不是资源标识的一部分，而是这次查询的上下文。
     * {@code /workouts/today} 已经唯一确定了一个资源（今天的训练），
     * 其余三个都是筛选项。而且这样客户端切换训练日不需要换 URL 模板。
     */
    @GetMapping("/today")
    public Result<TodayWorkoutResponse> today(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Integer day) {
        return Result.ok(todayWorkoutService.today(userId, programId, date, day));
    }
}
