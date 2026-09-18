package com.gymlog.stats;

import com.gymlog.common.QueryParamGuard;
import com.gymlog.common.Result;
import com.gymlog.stats.dto.ExerciseE1rmResponse;
import com.gymlog.stats.dto.PrBoardResponse;
import com.gymlog.stats.dto.StatsRangeQuery;
import com.gymlog.stats.dto.WeeklyStatsResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collections;

/**
 * 统计与图表接口。
 *
 * <p><b>手机端和 PC 端调的是同一批路径</b>（{@code AC-1-4}「无端专属接口」）
 * ——两端的数据形状完全一致，只是渲染方式不同（{@code AC-7B-1}）。
 * 所以这里**不做任何「为手机排好序」的处理**：肌群组数返回的是
 * 「周 × 肌群」的矩阵，手机端自己决定画横向条形还是别的东西。
 *
 * <p><b>只有 {@code now()} 允许出现在这一层</b>——Service 和纯函数一律接收
 * {@code today} 参数，否则测试没法固定时间。
 */
@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    /** 不传范围时的兜底：近 12 周（METRICS 4.3 / 4.4 的默认值） */
    private static final int DEFAULT_WEEKS = 12;

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /**
     * 周序列：容量 + 组数（按肌群）+ 训练次数 + 符合率。**一个请求喂三张图。**
     *
     * <pre>
     *   GET /api/v1/stats/weekly?from=2026-06-01&amp;to=2026-08-31
     * </pre>
     */
    @GetMapping("/weekly")
    public Result<WeeklyStatsResponse> weekly(@AuthenticationPrincipal Long userId,
                                              StatsRangeQuery query,
                                              HttpServletRequest request) {
        rejectUnknownParams(request);
        LocalDate today = LocalDate.now();
        LocalDate from = query.getFrom() == null
                ? today.minusWeeks(DEFAULT_WEEKS).with(java.time.DayOfWeek.MONDAY)
                : query.getFrom();
        LocalDate to = query.getTo() == null ? today : query.getTo();

        return Result.ok(statsService.weekly(userId, from, to, today));
    }

    /**
     * 单动作的估算 1RM 曲线（{@code METRICS 3.4}）。**手机端的主力入口。**
     *
     * <pre>
     *   GET /api/v1/stats/exercises/15/e1rm?from=2026-03-01&amp;to=2026-08-31
     * </pre>
     *
     * <p>{@code METRICS 3.6}：{@code metric_type != WEIGHT_REPS} 的动作
     * **不显示此图**。返回 {@code supported: false} 而不是 404——
     * 「这个动作没有 1RM 概念」是正常的，不是错误。
     */
    @GetMapping("/exercises/{exerciseId}/e1rm")
    public Result<ExerciseE1rmResponse> exerciseE1rm(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long exerciseId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        // 路径里已经有 exerciseId，剩下的合法参数只有 from / to
        QueryParamGuard.rejectUnknown(StatsRangeQuery.class,
                Collections.list(request.getParameterNames()));

        LocalDate today = LocalDate.now();
        LocalDate start = from == null ? today.minusMonths(6) : from;
        return Result.ok(statsService.e1rm(userId, exerciseId, start, to == null ? today : to));
    }

    /**
     * PR 看板。
     *
     * <pre>GET /api/v1/stats/prs</pre>
     *
     * <p><b>刻意不接受时间范围</b>——PR 的语义是「历史最高」，
     * 带范围会得到「本季度最佳」，而 {@code METRICS 7.3} 要显示「距今天数」。
     * 详见 {@link PrBoardResponse} 的类注释。
     */
    @GetMapping("/prs")
    public Result<PrBoardResponse> prs(@AuthenticationPrincipal Long userId,
                                       HttpServletRequest request) {
        // 不接受任何参数。用 rejectUnknown 传一个「有 from/to 字段的 DTO」当白名单
        // 是个陷阱——那会**放行** from/to，而本接口根本不看它们：
        // 客户端以为自己筛了范围，实际拿到全时段数据，两边都不报错。
        QueryParamGuard.rejectAll(Collections.list(request.getParameterNames()));
        return Result.ok(statsService.prs(userId, LocalDate.now()));
    }

    private void rejectUnknownParams(HttpServletRequest request) {
        QueryParamGuard.rejectUnknown(StatsRangeQuery.class,
                Collections.list(request.getParameterNames()));
    }
}
