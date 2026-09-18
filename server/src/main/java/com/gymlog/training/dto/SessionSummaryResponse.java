package com.gymlog.training.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 训练总结 —— 练完之后那一屏。
 *
 * <p>对应 M4-B-7「全部完成 → 训练总结页」。
 *
 * <h3>容量与组数是两个独立指标，不能只给一个</h3>
 *
 * <p>见 METRICS 4.0：
 * <ul>
 *   <li><b>容量</b>（kg）回答「总负荷在涨吗」——只看组数会漏掉「同样 15 组但重量翻倍」</li>
 *   <li><b>组数</b>回答「练得够不够」——只看容量会被大肌群主导，手臂和肩被压成看不见的线</li>
 * </ul>
 *
 * <p>所以两个都给，客户端并排显示。
 */
public record SessionSummaryResponse(

        Long id,
        String dayName,
        Integer weekNumber,
        boolean deload,

        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer durationSec,

        // ==================== 统计 ====================

        /**
         * 训练容量（kg）：{@code Σ(重量 × 次数)}，**仅正式组**。
         *
         * <p>自重动作按 {@code 体重 × bw_factor × 次数} 算。
         * 时长类动作（平板支撑）不计入。
         */
        BigDecimal volume,

        /** 正式组数（不含热身） */
        int workingSets,

        /** 热身组数。单独给出，让用户能看到自己热身做了多少 */
        int warmupSets,

        /**
         * 正式组的总次数。
         *
         * <p><b>不含热身组</b>，与 {@link #volume()}、{@link #workingSets()}
         * 口径一致。含进去的话会变成「正式组 2 组，共 30 次」这种
         * 自相矛盾的数字。
         */
        int totalReps,

        /** 总时长（秒）—— 时长类动作的合计，如平板支撑 */
        int totalDurationSec,

        // ==================== 完成度 ====================

        int exerciseCount,
        int completedExercises,
        int skippedExercises,

        /** 计划组数与实际组数的对比，如「18/15」表示多做了一组 */
        int plannedSets,

        // ==================== PR ====================

        /**
         * 本次刷新的个人纪录。
         *
         * <p>判定方式：该动作本次的最佳组 e1RM 超过历史所有场次。
         * 没有 PR 时是空列表，不是 null。
         */
        List<PersonalRecordItem> personalRecords,

        /**
         * 逐动作明细。
         *
         * <p>聚合数字回答「练了多少」，这里回答「**练的是什么**」——
         * 两者缺一不可：只有「容量 960kg」看不出是卧推还是深蹲推出来的。
         *
         * <p>同时带上**计划值**和**实际值**：展开之后能直接看出
         * 「计划做 4 组，我只做了 2 组」这种执行差异。
         */
        List<ExerciseSummary> exercises,

        /**
         * 与上一次同计划同训练日的对比。
         *
         * <p>没有上一次时为 null（第一次练这个训练日）。
         * 拿「上一次随便什么训练」来比没有意义——
         * 推日和腿日的容量本来就不在一个量级。
         */
        Comparison comparison

) {

    /**
     * 一个动作的本次明细。
     *
     * <p>紧凑显示（`60×8 · 60×8` 或压缩成 `20×10 ×3`）和展开显示
     * （计划 vs 实际）用的是**同一份数据**，由客户端决定怎么排版。
     *
     * <p>服务端不拼「60×8 · 60×8」这种字符串——
     * 那是展示逻辑，而且压缩规则（重复的组要不要合并）
     * 改一次就要动服务端。**结构化数据出，排版留在客户端。**
     */
    public record ExerciseSummary(
            Long exerciseId,
            String exerciseName,
            String metricType,
            String status,
            String statusLabel,
            /** 计划组数 */
            int plannedSets,
            /** 实际记录的组数（含热身） */
            int recordedSets,
            /** 该动作本次的容量（kg） */
            BigDecimal volume,
            /** 逐组明细，按组号对齐了计划与实际 */
            List<SetLine> sets
    ) {
    }

    /**
     * 一组的计划与实际。
     *
     * <p>按 {@code setNumber} 把两张表对齐：
     * <pre>
     *   用户临时加组 → 有 actual 没有 target
     *   用户少做几组 → 有 target 没有 actual（done = false）
     * </pre>
     * 两种都要能表达，所以两边的字段都可空。
     */
    public record SetLine(
            int setNumber,
            String setType,
            String setTypeLabel,

            // ---------- 计划 ----------
            BigDecimal targetWeight,
            Integer targetReps,
            Integer targetRepsMin,
            Integer targetRepsMax,
            /** 目标持续时长（秒）。null = 这一组不是按时间做的 */
            Integer targetDurationSec,
            Integer restSec,

            // ---------- 实际 ----------
            boolean done,
            BigDecimal actualWeight,
            Integer actualReps,
            Integer actualDurationSec
    ) {
    }

    /** 一个动作刷新的个人纪录 */
    public record PersonalRecordItem(
            Long exerciseId,
            String exerciseName,
            /** 本次的最佳组 e1RM */
            BigDecimal e1rm,
            /** 之前的历史最好成绩。null = 第一次做这个动作 */
            BigDecimal previousBest,
            /** 提升了多少 kg。第一次做时为 null */
            BigDecimal improvement
    ) {
    }

    /** 与上一次同训练日的对比 */
    public record Comparison(
            Long previousSessionId,
            LocalDateTime previousStartedAt,

            BigDecimal volumeDelta,
            Integer workingSetsDelta,
            Integer durationSecDelta,

            /** 逐动作对比，只列出两次都有的动作 */
            List<ExerciseDelta> exercises
    ) {
    }

    /** 单个动作的两次对比 */
    public record ExerciseDelta(
            Long exerciseId,
            String exerciseName,
            /** 本次最佳组重量。null = 本次没做（跳过） */
            BigDecimal currentBestWeight,
            /** 上次最佳组重量 */
            BigDecimal previousBestWeight,
            /** 重量差（kg） */
            BigDecimal weightDelta,
            /** 本次该动作的容量 */
            BigDecimal currentVolume,
            BigDecimal previousVolume
    ) {
    }
}
