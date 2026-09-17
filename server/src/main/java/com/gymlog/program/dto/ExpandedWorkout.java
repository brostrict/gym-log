package com.gymlog.program.dto;

import com.gymlog.program.TargetWeightType;
import com.gymlog.training.SetType;

import java.math.BigDecimal;
import java.util.List;

/**
 * 展开后的训练内容 —— 「第 N 周第 M 天具体练什么」。
 *
 * <p>这是周期化展开算法的输出。它把「第 5 周 +5%」这样的**修饰**
 * 展开成每一组的具体目标。
 *
 * <h3>为什么目标强度是一个四元组，而不是一个重量数字</h3>
 *
 * <p>因为 **RPE 类型的处方根本算不出具体重量**。
 *
 * <p>RPE 描述的是「练到什么程度」（RPE 8 = 还能再做 2 次），
 * 不是「用多重」。同一个 RPE 8，状态好的那天可能是 80kg，
 * 状态差的那天可能只有 70kg——**这正是 RPE 存在的意义**。
 *
 * <p>如果返回类型是 {@code BigDecimal weight}，遇到 RPE 处方就只能：
 * <ul>
 *   <li>返回 null —— 调用方要额外判空，且丢掉了「这是 RPE 8」的信息</li>
 *   <li>编一个数字 —— <b>最糟的选择</b>，用户会照着错误的重量练</li>
 * </ul>
 *
 * <p>所以用 {@code type + 三个可空值}，与 {@code prescribed_exercise}
 * 的存储结构一致。前端按 {@code type} 决定显示「60kg」还是「RPE 8」。
 */
public record ExpandedWorkout(

        Long programId,
        String programName,

        Integer weekNumber,
        Integer dayNumber,
        String dayName,

        /** 是否减量周。跟练界面可以据此显示不同的提示 */
        boolean deloadWeek,

        /** 本周的重量调整百分比（已应用）。用于界面提示「本周加重 5%」 */
        BigDecimal weightAdjustPct,

        List<ExerciseItem> exercises

) {

    /**
     * 展开后的一个动作。
     *
     * <p><b>超级组信息原样保留</b>：展开算法不改变超级组语义，
     * 只把 `supersetGroup` / `orderInGroup` 带到输出上，
     * 由跟练状态机（Phase 3）去处理执行顺序。
     */
    public record ExerciseItem(

            Long exerciseId,
            String exerciseName,
            String primaryMuscle,
            String primaryMuscleLabel,

            /** 计量类型。决定跟练界面显示哪些输入控件 */
            String metricType,

            Integer orderIndex,

            /** 超级组编号。NULL = 普通动作 */
            Integer supersetGroup,
            Integer orderInGroup,

            /** 展开后的组列表。长度 = 目标组数（含周修饰调整） */
            List<SetItem> sets,

            String note

    ) {
    }

    /**
     * 展开后的一组。
     *
     * <p>注意 {@code targetReps} 和 {@code targetRepsMin/Max} 的关系：
     * 固定次数时只填 {@code targetReps}；区间次数时填 min/max。
     * 前端优先看 min/max，为空再退回 targetReps。
     */
    public record SetItem(

            Integer setNumber,
            SetType setType,
            String setTypeLabel,

            /** 目标强度。可能为空（既没有重量也没有 RPE） */
            Target target,

            Integer targetReps,
            Integer targetRepsMin,
            Integer targetRepsMax,

            /** 本组之后的休息秒数（已解析，不会是 null） */
            Integer restSec,

            String note

    ) {
    }

    /**
     * 目标强度。
     *
     * <p>三个值最多只有一个非空，由 {@link #type} 决定哪个有意义。
     */
    public record Target(

            TargetWeightType type,

            /** type = ABSOLUTE 时有值，单位 kg */
            BigDecimal weight,

            /** type = PERCENT_1RM 时有值，如 75 表示 75% */
            BigDecimal pct,

            /** type = RPE 时有值，如 8 */
            BigDecimal rpe

    ) {

        public static Target ofWeight(BigDecimal weight) {
            return new Target(TargetWeightType.ABSOLUTE, weight, null, null);
        }

        public static Target ofPct(BigDecimal pct) {
            return new Target(TargetWeightType.PERCENT_1RM, null, pct, null);
        }

        public static Target ofRpe(BigDecimal rpe) {
            return new Target(TargetWeightType.RPE, null, null, rpe);
        }

        /** 供日志和调试用的简短描述 */
        public String describe() {
            if (type == null) {
                return "无目标";
            }
            return switch (type) {
                case ABSOLUTE -> weight + "kg";
                case PERCENT_1RM -> pct + "%1RM";
                case RPE -> "RPE " + rpe;
            };
        }
    }
}
