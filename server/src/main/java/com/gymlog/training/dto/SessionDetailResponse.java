package com.gymlog.training.dto;

import com.gymlog.training.SessionExercise;
import com.gymlog.training.SessionSetTarget;
import com.gymlog.training.WorkoutSession;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次训练的内容 —— 从快照读出来，**不回查计划**。
 *
 * <h3>为什么每个字段都来自快照</h3>
 *
 * <p>用户改计划之后，这个接口返回的内容必须和当时一模一样。
 * 任何一处偷偷回查了计划，历史记录就会「追溯性地改变含义」——
 * 而用户看到的只是「我明明记得那天推的是 60kg，怎么变成 65 了」。
 *
 * <p>所以这个 DTO 的组装方法**只接受快照实体**，
 * 连 {@code Program} 都不传进来，从签名上就杜绝了回查。
 */
public record SessionDetailResponse(

        Long id,

        /** 来源计划。null = 临时训练 */
        Long programId,

        // ---------- 快照：这是哪一天 ----------
        Integer dayNumber,
        String dayName,

        // ---------- 快照：周期化位置 ----------
        Integer weekNumber,
        boolean deload,
        BigDecimal weightAdjustPct,

        // ---------- 状态 ----------
        String status,
        String statusLabel,

        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer durationSec,

        String note,

        /**
         * 是否返回的是**已存在的**进行中会话（而不是新建的）。
         *
         * <p>一个人同时只能练一场，所以服务端保证同一用户最多一个进行中会话。
         * 客户端点「开始训练」时如果已经有一场没结束，
         * 拿到的就是那一场，并把 {@code resumed} 置为 true——
         * 界面据此显示「继续上次训练」而不是「今天的训练」。
         */
        boolean resumed,

        List<ExerciseItem> exercises

) {

    /**
     * 快照动作。
     *
     * <p>{@code exerciseId} 可能指向一个已被删除的动作（用户删了自己的自定义动作），
     * 但 {@code exerciseName} 永远可用——这正是两列都存的原因。
     */
    public record ExerciseItem(
            Long id,
            Long exerciseId,
            String exerciseName,
            String primaryMuscle,
            String metricType,
            Integer orderIndex,
            Integer supersetGroup,
            Integer orderInGroup,
            Integer targetSets,
            String status,
            String statusLabel,
            String note,
            List<SetTargetItem> sets
    ) {
    }

    /**
     * 每组的目标（快照值，已展开）。
     *
     * <p>{@code target.weight} 是**展开后**的重量。
     * 计划里写「第 5 周 +5%」，这里给的是算好的 63kg——
     * 客户端不需要（也不应该）自己做这个乘法。
     */
    public record SetTargetItem(
            Integer setNumber,
            String setType,
            String setTypeLabel,
            Integer targetReps,
            Integer targetRepsMin,
            Integer targetRepsMax,
            BigDecimal weight,
            BigDecimal pct,
            BigDecimal rpe,
            String weightType,
            Integer restSec,
            String note
    ) {

        public static SetTargetItem from(SessionSetTarget t) {
            return new SetTargetItem(
                    t.getSetNumber(),
                    t.getSetType() == null ? null : t.getSetType().name(),
                    t.getSetType() == null ? null : t.getSetType().getDisplayName(),
                    t.getTargetReps(),
                    t.getTargetRepsMin(),
                    t.getTargetRepsMax(),
                    t.getTargetWeight(),
                    t.getTargetWeightPct(),
                    t.getTargetRpe(),
                    t.getTargetWeightType() == null ? null : t.getTargetWeightType().name(),
                    t.getRestSec(),
                    t.getNote());
        }
    }

    // ==================================================================
    // 组装
    // ==================================================================

    /**
     * 组装详情。
     *
     * <p><b>注意参数里没有 {@code Program}，也没有 {@code PrescribedExercise}</b>——
     * 这不是疏忽，是刻意的：从签名上就不可能回查计划。
     */
    public static SessionDetailResponse assemble(WorkoutSession session,
                                                 List<ExerciseItem> exercises,
                                                 boolean resumed) {
        return new SessionDetailResponse(
                session.getId(),
                session.getProgramId(),
                session.getDayNumber(),
                session.getDayName(),
                session.getWeekNumber(),
                session.isDeload(),
                session.getWeightAdjustPct(),
                session.getStatus() == null ? null : session.getStatus().name(),
                session.getStatus() == null ? null : session.getStatus().getDisplayName(),
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getDurationSec(),
                session.getNote(),
                resumed,
                exercises);
    }

    /** 从快照实体构造动作条目（逐组目标由调用方填充） */
    public static ExerciseItem exerciseFrom(SessionExercise e, List<SetTargetItem> sets) {
        return new ExerciseItem(
                e.getId(),
                e.getExerciseId(),
                e.getExerciseName(),
                e.getPrimaryMuscle(),
                e.getMetricType(),
                e.getOrderIndex(),
                e.getSupersetGroup(),
                e.getOrderInGroup(),
                e.getTargetSets(),
                e.getStatus() == null ? null : e.getStatus().name(),
                e.getStatus() == null ? null : e.getStatus().getDisplayName(),
                e.getNote(),
                sets);
    }
}
