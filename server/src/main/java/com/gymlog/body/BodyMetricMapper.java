package com.gymlog.body;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BodyMetric 数据访问接口。
 *
 * <p>大部分查询走 MyBatis-Plus 的 {@code LambdaQueryWrapper} 就够了——
 * 条件都是等值和区间，没有聚合。
 *
 * <p><b>这里刻意不写「按天分组取平均」的 SQL。</b>
 * {@code METRICS 1.2} 的日聚合是两步式的（先合并单日多次测量，再算窗口平均），
 * 而且第一步的边界是「Asia/Shanghai 自然日」——那是一组**规则**，
 * 不是一个 {@code GROUP BY DATE(measured_at)} 能表达的。
 * 规则放在 {@code BodySeries} 纯函数里，SQL 只负责把区间内的原始行取出来。
 * 一个区间最多几百行，不值得为它把规则劈成两半。
 */
@Mapper
public interface BodyMetricMapper extends BaseMapper<BodyMetric> {

    /**
     * 某指标在区间内的**全部原始测量**，按时间升序。
     *
     * <p>为什么取原始行而不是在 SQL 里聚合：见类注释。
     * 为什么升序：调用方要做「按自然日分组」，而分组需要时间有序。
     * 让 SQL 排好比在 Java 里再排一次便宜，而且意图更清楚。
     *
     * @param site    部位。传 {@link BodySite#NONE} 查「无部位」的指标（体重、体脂……）。
     *                <b>不能传 null</b>——那会拼出 {@code site IS NULL}，永远查不到行，
     *                因为这一列是 NOT NULL。
     */
    @Select("""
            SELECT id, user_id, metric_type, site, value, measured_at,
                   measure_condition, note, created_at, updated_at
            FROM body_metric
            WHERE user_id = #{userId}
              AND metric_type = #{metricType}
              AND site = #{site}
              AND measured_at >= #{from}
              AND measured_at <= #{to}
            ORDER BY measured_at ASC
            """)
    List<BodyMetric> selectSeries(@Param("userId") Long userId,
                                  @Param("metricType") BodyMetricType metricType,
                                  @Param("site") BodySite site,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);

    /**
     * 某指标**有数据的部位**，用于「只显示有数据的部位」。
     *
     * <p>{@code METRICS 2.5}：只测了部分部位时，只显示有数据的部位的小图，
     * 不显示空图。所以要先把「哪些部位有数据」查出来。
     *
     * <p>⚠️ <b>返回的是字符串而不是 {@link BodySite}</b>，转换交给调用方。
     * 这条 SQL 的结果映射走的是 MyBatis 的默认枚举处理器，
     * 而它和 MyBatis-Plus 给实体字段用的那套不是同一条路径——
     * 拿不准的地方不做假设，返回字符串再由 {@code BodySeries.usedSites}
     * 显式转换，失败了也是当场可见的。
     */
    @Select("""
            SELECT DISTINCT site FROM body_metric
            WHERE user_id = #{userId} AND metric_type = #{metricType}
            """)
    List<String> selectUsedSiteCodes(@Param("userId") Long userId,
                                     @Param("metricType") BodyMetricType metricType);
}
