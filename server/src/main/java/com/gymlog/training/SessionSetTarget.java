package com.gymlog.training;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gymlog.program.TargetWeightType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 每组目标快照，对应 {@code session_set_target} 表。
 *
 * <p><b>这里存的是「展开后」的结果，不是原始处方。</b>
 *
 * <p>这是这张表存在的全部意义。计划里写的是「第 5 周 +5%」和
 * 「第三组 70kg」，展开之后才是「第一组 63kg、第二组 63kg、第三组 73.5kg」。
 * 快照必须冻结**展开后**的值——
 *
 * <p>否则用户改一下周调整，或者过几周再看，
 * 就得拿当时的计划重新展开一遍才能知道「那天到底该推多重」，
 * 而计划早就被改过了。
 *
 * <h3>为什么目标强度是四元组而不是一个裸重量</h3>
 *
 * <p>因为 RPE 处方算不出具体重量。详见
 * {@code com.gymlog.program.dto.ExpandedWorkout.Target}。
 *
 * <p>V1 的展开只产出 {@code ABSOLUTE}（其余类型会明确报错），
 * 但表结构保留完整——将来支持 %1RM 时不用改表。
 *
 * <h3>restSec 不会是 null</h3>
 *
 * <p>展开时已经解析过优先级链（逐组 &gt; 动作级 &gt; 默认 90 秒）。
 * 跟练倒计时必须有个确定的数字，不能到运行时才发现是空的。
 */
@Data
@TableName("session_set_target")
public class SessionSetTarget {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionExerciseId;

    /** 第几组，从 1 开始 */
    private Integer setNumber;

    private SetType setType;

    // ---------- 目标次数 ----------

    /** 固定目标次数 */
    private Integer targetReps;

    /** 区间次数下限 */
    private Integer targetRepsMin;

    /** 区间次数上限 */
    private Integer targetRepsMax;

    // ---------- 目标时长（等长收缩动作）----------

    /**
     * 目标持续时长（秒）。null = 本组没有时长目标（卧推这类就不该有）。
     *
     * <p>倒计时从这个值开始数，数到 0 即达成目标；继续撑下去的部分
     * 由客户端转成正计时记录成「已超 N 秒」。
     *
     * <p>⚠️ 在 V14 之前，这个秒数是**塞在 {@link #targetRepsMin} 里**的。
     */
    private Integer targetDurationSec;

    /**
     * 倒计时期间的播报间隔（秒）。{@code 0} = 不间隔播报。
     *
     * <p>展开时已解析，不会是 null——和 {@link #restSec} 同样的约定。
     */
    private Integer announceIntervalSec;

    // ---------- 目标强度 ----------

    private TargetWeightType targetWeightType;

    private BigDecimal targetWeight;

    private BigDecimal targetWeightPct;

    private BigDecimal targetRpe;

    /** 本组之后的休息秒数。展开时已解析，不会是 null */
    private Integer restSec;

    private String note;

    private LocalDateTime createdAt;

    // ==================================================================
    // 便捷判断
    // ==================================================================

    /** 是否是区间次数（而非固定次数） */
    public boolean isRepRange() {
        return targetRepsMin != null && targetRepsMax != null
                && !targetRepsMin.equals(targetRepsMax);
    }
}
