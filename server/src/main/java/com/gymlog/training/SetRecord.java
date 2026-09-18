package com.gymlog.training;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 组记录，对应 {@code set_record} 表 —— 用户**实际**做的那一组。
 *
 * <h3>和 {@link SessionSetTarget} 的区别</h3>
 * <pre>
 *   session_set_target   计划：这一组**应该**推多重
 *   set_record           实际：这一组**实际**推了多重
 * </pre>
 *
 * <p>两者按 {@code (sessionExerciseId, setNumber)} 对齐，但**不是一对一**：
 * 用户可以临时加组、少做几组、或者不按目标重量做（这是常态）。
 *
 * <p>所以必须是两张表。合并的话「计划」和「实际」就分不开了，
 * 而这两者的差值正是**符合率**这个指标的全部意义。
 *
 * <h3>宽字段而不是 EAV</h3>
 *
 * <p>哪几列有值由动作的 {@code metricType} 决定：
 * <pre>
 *   WEIGHT_REPS        weight + reps
 *   REPS_ONLY          reps
 *   DURATION           durationSec
 *   DISTANCE_DURATION  durationSec + distanceM
 * </pre>
 *
 * <p>用 EAV（一行一个属性）的话，「某动作的总容量」要 join 一张属性表再透视。
 * 计量类型只有四种且稳定，宽表更合适。
 */
@Data
@TableName("set_record")
public class SetRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionExerciseId;

    /**
     * 第几组。
     *
     * <p><b>同时是离线同步的幂等键</b>：唯一索引
     * {@code (session_exercise_id, set_number)} 保证重放「记录第 3 组」
     * 不会产生两条。
     */
    private Integer setNumber;

    /**
     * 组类型。
     *
     * <p>可以**在训练中改变**：计划里是正式组，用户练到力竭标成力竭组。
     * 所以独立存一份，不引用 {@code session_set_target} 的值。
     */
    private SetType setType;

    // ---------- 计量字段 ----------

    private BigDecimal weight;

    private Integer reps;

    private Integer durationSec;

    private BigDecimal distanceM;

    private BigDecimal rpe;

    /** 本组之后的**实际**休息秒数。NULL = 没记录（如最后一组之后） */
    private Integer restActualSec;

    private String note;

    /** 完成时刻。由客户端上报——离线训练时服务端不在场 */
    private LocalDateTime completedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================================================================
    // 便捷判断
    // ==================================================================

    /**
     * 是否计入训练容量。
     *
     * <p><b>热身组不计入</b>——这是全局口径（REQUIREMENTS 术语表）。
     * 混进去会让所有趋势失真：热身组往往次数多、重量轻，
     * 而且因人因日而异，是最不稳定的一块。
     */
    public boolean countsTowardVolume() {
        return setType != SetType.WARMUP;
    }
}
