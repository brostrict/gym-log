package com.gymlog.program.dto;

import com.gymlog.exercise.MetricType;
import com.gymlog.exercise.MuscleGroup;
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

            /**
             * 主要肌群。
             *
             * <p>用枚举而不是 String：展开函数手里本来就是枚举
             * （{@code exercise.getPrimaryMuscle()}），转成字符串再转回来
             * 只是多两次转换和多一个写错的机会。
             *
             * <p>Jackson 默认按 {@code name()} 序列化，所以 JSON 形状不变。
             */
            MuscleGroup primaryMuscle,

            /** 肌群的中文标签，省得客户端再维护一份映射 */
            String primaryMuscleLabel,

            /** 计量类型。决定跟练界面显示哪些输入控件 */
            MetricType metricType,

            /**
             * 自重系数。只有 {@code REPS_ONLY} 动作有值。
             *
             * <p><b>⚠️ 少了它，自重动作的容量永远是 0。</b>
             *
             * <p>这个字段是 4B 时才补上的，但补的不是新功能——{@code V12}
             * 就加了 {@code session_exercise.bw_factor} 列，注释写着
             * 「容量 = 体重 × bw_factor × 次数」，而 {@code TrainingMetrics.setVolume}
             * 一直在读它。缺的是**中间这一段**：展开函数不往外带，
             * 会话快照就无从拷贝。
             *
             * <p>为什么一直没被发现：读不到时 {@code setVolume} 返回 <b>0</b>，
             * 而 0 正是 {@code METRICS 4.1}「没记体重就不计容量」的**合法值**。
             * 于是「引体向上容量 0」看起来完全正常，直到 4B 真的记了体重——
             * 那时它还是 0，才露出来。
             */
            BigDecimal bwFactor,

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

            /**
             * 目标持续时长（秒）。null = 本组没有时长目标。
             *
             * <p>只有 DURATION / DISTANCE_DURATION 类动作才会有值——
             * 展开时从动作级处方解析，所以这里一定是确定的数字，不需要客户端再回落。
             */
            Integer targetDurationSec,

            /** 倒计时期间的播报间隔（秒），0 = 不间隔播报。已解析，不会是 null */
            Integer announceIntervalSec,

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
