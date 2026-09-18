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
         * 与上一次同计划同训练日的对比。
         *
         * <p>没有上一次时为 null（第一次练这个训练日）。
         * 拿「上一次随便什么训练」来比没有意义——
         * 推日和腿日的容量本来就不在一个量级。
         */
        Comparison comparison

) {

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
