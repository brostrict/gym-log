package com.gymlog.program;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gymlog.training.SetType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 逐组处方，对应 {@code prescribed_set} 表。
 *
 * <p><b>这张表是「计划灵活度」的关键。</b>只支持动作级统一目标会挡掉大量真实计划：
 *
 * <pre>
 *   5×5 递增： 60 / 65 / 70 / 70 / 70 kg
 *   5/3/1：    75% / 85% / 95%
 *   金字塔：   12 / 10 / 8 / 6 次
 *   递减组：   最后一组做完立即减重
 * </pre>
 *
 * <p><b>只为需要特殊目标的组建记录</b>——「3组×10次」用动作级默认值即可，
 * 不必建三条一模一样的记录。展开时：有记录用记录，没记录回落默认值。
 */
@Data
@TableName("prescribed_set")
public class PrescribedSet {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long prescribedExerciseId;

    /** 第几组，从 1 开始 */
    private Integer setNumber;

    /** 组类型。热身组不计入容量统计 */
    private SetType setType;

    /** 本组固定目标次数。NULL = 用动作级默认 */
    private Integer targetReps;

    private Integer targetRepsMin;

    private Integer targetRepsMax;

    /** 本组目标重量。NULL = 用动作级默认 */
    private BigDecimal targetWeight;

    private BigDecimal targetWeightPct;

    private BigDecimal targetRpe;

    /** 本组后的休息秒数。NULL = 用动作级默认 */
    private Integer restSec;

    private String note;

    private LocalDateTime createdAt;
}
