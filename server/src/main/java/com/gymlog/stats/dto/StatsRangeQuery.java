package com.gymlog.stats.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * 统计接口的时间范围参数。
 *
 * <p><b>为什么包成一个 DTO 而不是两个 {@code @RequestParam}</b>：
 * 这个项目有过一次教训——查询参数名拼错会被 Spring **静默忽略**，
 * 接口照样返回 200 和一堆数据（见 {@code QueryParamGuard}）。
 * 那次的断言「有结果」太弱，一开始还让它通过了。
 *
 * <p>{@code /stats/weekly?form=2026-06-01&too=...} 这种拼写错误如果被忽略，
 * 用户会拿到**默认范围**的数据，看起来完全正常——是最难发现的一类 bug。
 * 包成 DTO 之后可以调 {@code QueryParamGuard.rejectUnknown}，白名单由字段反射得出。
 *
 * <p>{@code from} 必填：默认范围在 {@code METRICS} 里各图不同（容量 12 周、
 * 频率 26 周、e1RM 6 个月），服务端给不出一个「正确的默认值」。
 * 客户端每次都显式传，服务端只做兜底。
 */
@Data
public class StatsRangeQuery {

    /** 起始日期（含），格式 {@code yyyy-MM-dd} */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    /** 结束日期（含）。不传则等于 {@code from} */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
