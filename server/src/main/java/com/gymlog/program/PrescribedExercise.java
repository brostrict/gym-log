package com.gymlog.program;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 处方动作，对应 {@code prescribed_exercise} 表。
 *
 * <p>训练日里的一个动作，以及「目标是什么」。
 *
 * <p><b>两层目标结构</b>：
 * <pre>
 *   prescribed_exercise（动作级默认）  ← 大多数动作只需要这一层
 *     └─ prescribed_set（逐组覆盖）    ← 只有递增/递减组才需要
 * </pre>
 * 展开时：有逐组记录就用它，否则回落到动作级默认值。
 * 这样「3组×10次」这种统一目标不用建三条冗余记录。
 */
@Data
@TableName("prescribed_exercise")
public class PrescribedExercise {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long dayTemplateId;

    private Long exerciseId;

    private Integer orderIndex;

    // ==================== 超级组 ====================

    /**
     * 超级组编号。NULL 表示普通动作。
     *
     * <p>同一个训练日里 {@code supersetGroup} 相同的动作构成一个超级组。
     *
     * <p><b>超级组不是独立实体</b>，只是动作上的分组标记。
     * 执行时组内动作之间不休息，一轮做完才休息。
     * 详见 TIMER-SPEC 1.5。
     */
    private Integer supersetGroup;

    /** 组内执行顺序，从 1 开始。仅超级组内有值 */
    private Integer orderInGroup;

    // ==================== 目标（动作级默认）====================

    private Integer targetSets;

    private Integer targetRepsMin;

    /** 次数上限。等于 min 时表示固定次数 */
    private Integer targetRepsMax;

    /**
     * 目标持续时长（秒）。仅 {@code DURATION} / {@code DISTANCE_DURATION} 有意义。
     *
     * <p>倒计时从这个值开始数，数到 0 即达成目标；继续撑下去的部分
     * 由客户端转成正计时记录成「已超 N 秒」。
     *
     * <p>⚠️ 在 V14 之前，这个秒数是**塞在 {@link #targetRepsMin} 里**的
     * （模板 JSON 里写着 {@code "note":"目标是秒数"}）。V14 把它搬了过来。
     */
    private Integer targetDurationSec;

    /**
     * 倒计时期间的播报间隔（秒）。{@code 0} = 不间隔播报。
     *
     * <p>NULL 表示用默认值（{@code ProgramExpander.DEFAULT_ANNOUNCE_INTERVAL_SEC}）。
     */
    private Integer announceIntervalSec;

    private Integer restSec;

    private TargetWeightType targetWeightType;

    /** 目标重量（kg）。仅 ABSOLUTE 时有效 */
    private BigDecimal targetWeight;

    /** 目标 %1RM。仅 PERCENT_1RM 时有效 */
    private BigDecimal targetWeightPct;

    /** 目标 RPE。仅 RPE 时有效 */
    private BigDecimal targetRpe;

    private String note;

    private LocalDateTime createdAt;

    // ==================================================================
    // 便捷判断
    // ==================================================================

    /** 是否属于超级组 */
    public boolean isInSuperset() {
        return supersetGroup != null;
    }

    /**
     * 目标次数是否为一个区间（而非固定值）。
     *
     * <p>区间和固定值的区别影响完成率的计算：
     * 区间是「8-12 次都算达标」，固定值是「必须正好 10 次」。
     */
    public boolean isRepRange() {
        return targetRepsMin != null && targetRepsMax != null
                && !targetRepsMin.equals(targetRepsMax);
    }
}
