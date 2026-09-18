package com.gymlog.body;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 身体数据，对应 {@code body_metric} 表 —— 一次测量：一个指标、一个部位、一个数。
 *
 * <h3>为什么是窄表，而 {@link com.gymlog.training.SetRecord} 是宽表</h3>
 *
 * <p>同一个项目里两种相反的建模，区别在两处：
 *
 * <table border="1">
 *   <caption>窄表 vs 宽表的判据</caption>
 *   <tr><th></th><th>set_record（宽）</th><th>body_metric（窄）</th></tr>
 *   <tr><td>判别器</td><td>封闭：4 种计量类型</td><td>开放：今天 14 个，明天可能 15 个</td></tr>
 *   <tr><td>一行有几个值</td><td>多个（重量+次数+时长+距离）</td><td><b>一个</b></td></tr>
 * </table>
 *
 * <p>{@code SetRecord} 的注释里写着「用宽字段而不是 EAV」——那条论证针对的是
 * 「一行多个**相关**量」的记录（查总容量要 {@code weight * reps}）。
 * 而这里一行就是一个标量，「今天 72.4kg」没有可透视的东西。
 *
 * <p>反过来，用宽表存身体数据的话，想加一个「握力」就要 {@code ALTER TABLE}。
 *
 * <h3>⚠️ site 是 NONE 而不是 null</h3>
 *
 * <p>唯一键 {@code (user_id, metric_type, site, measured_at)} 是幂等键，
 * 而 MySQL 的唯一索引把 NULL 当作互不相等——site 可空的话，
 * 「同一时刻的体重」可以插进去任意多条，幂等对 12 个指标直接失效。
 * 详见 {@link BodySite} 的类注释。
 */
@Data
@TableName("body_metric")
public class BodyMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private BodyMetricType metricType;

    /** 部位。没有部位概念的指标是 {@link BodySite#NONE}，**不是 null** */
    private BodySite site;

    private BigDecimal value;

    /**
     * 测量时刻。
     *
     * <p><b>由客户端上报</b>——离线记录时服务端不在场。
     * 和 {@code set_record.completed_at} 同理：这是业务字段，不是审计字段。
     */
    private LocalDateTime measuredAt;

    /**
     * 测量条件。字段名不是 {@code condition}——那是 MySQL 保留字。
     *
     * <p>不是体重专属：静息心率要求晨起测，训练前后的体重能差 1kg 以上。
     * 见 {@link MetricCondition}。
     */
    private MetricCondition measureCondition;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
