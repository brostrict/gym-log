package com.gymlog.body;

import com.gymlog.body.dto.BodyMetricRequest;
import com.gymlog.body.dto.BodyMetricResponse;
import com.gymlog.body.dto.BodyMetricTypeResponse;
import com.gymlog.body.dto.BodySeriesResponse;
import com.gymlog.body.dto.DerivedMetricResponse;
import com.gymlog.common.QueryParamGuard;
import com.gymlog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 身体数据接口。
 *
 * <p>手机端和 PC 端调同一批路径（{@code AC-1-4}）。
 *
 * <p><b>只有 {@code now()} 允许出现在这一层</b>——Service 和纯函数一律接收
 * {@code from}/{@code to} 参数，否则测试没法固定时间。
 * （和 {@code StatsController} 同一条规矩）
 */
@RestController
@RequestMapping("/api/v1/body")
public class BodyController {

    /** 序列的兜底范围：近 3 个月。{@code METRICS} 各图默认范围不同，这只是兜底 */
    private static final int DEFAULT_RANGE_MONTHS = 3;

    /** 「最近记录」列表的默认条数 */
    private static final int DEFAULT_LIST_LIMIT = 30;

    private final BodyService bodyService;

    public BodyController(BodyService bodyService) {
        this.bodyService = bodyService;
    }

    /**
     * 所有身体指标的元数据 —— 客户端据此生成录入表单。
     *
     * <pre>GET /api/v1/body/metric-types</pre>
     *
     * <p><b>不带用户维度</b>：这是静态定义，不因用户而异。
     * 客户端可以放心缓存。
     */
    @GetMapping("/metric-types")
    public Result<List<BodyMetricTypeResponse>> metricTypes(HttpServletRequest request) {
        QueryParamGuard.rejectAll(Collections.list(request.getParameterNames()));
        return Result.ok(bodyService.metricTypes());
    }

    /**
     * 记录一次测量。**幂等**：同一 (指标, 部位, 测量时刻) 重复提交是覆盖。
     *
     * <pre>POST /api/v1/body/metrics</pre>
     *
     * <p>用 POST 而不是 PUT：客户端不知道这条记录在服务端的 id
     * （离线补录时更没有），而幂等性由业务键保证，不由 HTTP 方法保证。
     */
    @PostMapping("/metrics")
    public Result<BodyMetricResponse> record(@AuthenticationPrincipal Long userId,
                                             @Valid @RequestBody BodyMetricRequest body) {
        return Result.ok(bodyService.record(userId, body));
    }

    /**
     * 某指标的原始记录，倒序（最近的在最前）。
     *
     * <pre>GET /api/v1/body/metrics?metricType=WEIGHT&amp;from=2026-06-01&amp;limit=30</pre>
     *
     * <p>{@code metricType} 必填：没有「所有指标混在一起」的列表——
     * 体重和睡眠质量放在一个列表里没法读。
     */
    @GetMapping("/metrics")
    public Result<List<BodyMetricResponse>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam BodyMetricType metricType,
            @RequestParam(required = false) BodySite site,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer limit,
            HttpServletRequest request) {
        QueryParamGuard.rejectUnknown(BodyListQuery.class,
                Collections.list(request.getParameterNames()));
        return Result.ok(bodyService.list(userId, metricType, site, from, to,
                limit == null ? DEFAULT_LIST_LIMIT : limit));
    }

    /**
     * 删除一条记录。
     *
     * <pre>DELETE /api/v1/body/metrics/123</pre>
     *
     * <p><b>不会改变任何历史训练会话的容量</b>——会话在创建时就快照了
     * {@code body_weight_kg}，那是有意为之。见 {@link BodyService#delete}。
     */
    @DeleteMapping("/metrics/{id}")
    public Result<Void> delete(@AuthenticationPrincipal Long userId,
                               @PathVariable Long id) {
        bodyService.delete(userId, id);
        return Result.ok();
    }

    /**
     * 从自测数据推导出的指标 —— BMI / 体脂率估算 / BMR / 腰高比。
     *
     * <pre>GET /api/v1/body/derived</pre>
     *
     * <p><b>不接受任何参数</b>：这是「此刻的四个数」，不是序列。
     * 没有 {@code from}/{@code to}——派生值的趋势和体重曲线完全等价
     * （见 {@code BodyDerived} 的类注释），出了就是装饰。
     */
    @GetMapping("/derived")
    public Result<List<DerivedMetricResponse>> derived(
            @AuthenticationPrincipal Long userId,
            HttpServletRequest request) {
        QueryParamGuard.rejectAll(Collections.list(request.getParameterNames()));
        return Result.ok(bodyService.derived(userId, LocalDate.now()));
    }

    /**
     * 趋势序列 —— 一张图要的全部数据。
     *
     * <pre>GET /api/v1/body/series?metricType=WEIGHT&amp;from=2026-06-01&amp;to=2026-08-31</pre>
     *
     * <p>围度要带 {@code site}（{@code AC-7B-4}：手机端围度图一次只显示一个部位）。
     * 不传会被拒（{@code 60005}）——「围度趋势」如果是 12 个部位混在一起的一条线，
     * 那个数没有任何意义。
     */
    @GetMapping("/series")
    public Result<BodySeriesResponse> series(
            @AuthenticationPrincipal Long userId,
            @RequestParam BodyMetricType metricType,
            @RequestParam(required = false) BodySite site,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest request) {
        QueryParamGuard.rejectUnknown(BodySeriesQuery.class,
                Collections.list(request.getParameterNames()));

        LocalDate today = LocalDate.now();
        LocalDate start = from == null ? today.minusMonths(DEFAULT_RANGE_MONTHS) : from;
        return Result.ok(bodyService.series(userId, metricType,
                site == null ? BodySite.NONE : site,
                start, to == null ? today : to));
    }

    // ==================================================================
    // 参数白名单
    // ==================================================================
    //
    // 这两个类是**纯粹的白名单载体**，不会被实例化，也不参与绑定。
    //
    // 为什么要单独声明而不是复用：拼错的查询参数会被 Spring 静默忽略
    // （见 QueryParamGuard 的注释），所以每个接口都要声明自己**接受**哪些参数。
    // 直接用 @RequestParam 列表也可以，但那样白名单和实际读取是两处，
    // 漏一个不会报错——4A 已经踩过一次（用「有 from/to 的 DTO」当白名单，
    // 放行了两个接口根本不看的参数）。

    @SuppressWarnings("unused")
    private static final class BodyListQuery {
        private BodyMetricType metricType;
        private BodySite site;
        private LocalDate from;
        private LocalDate to;
        private Integer limit;
    }

    @SuppressWarnings("unused")
    private static final class BodySeriesQuery {
        private BodyMetricType metricType;
        private BodySite site;
        private LocalDate from;
        private LocalDate to;
    }
}
