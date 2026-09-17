package com.gymlog.exercise.dto;

import com.gymlog.exercise.Exercise;

import java.math.BigDecimal;

/**
 * 动作响应。
 *
 * <p><b>为什么同时返回「编码」和「中文标签」</b>：
 * <pre>
 *   "primaryMuscle": "CHEST",          ← 前端做筛选、对比、传参用
 *   "primaryMuscleLabel": "胸"          ← 前端直接显示用
 * </pre>
 *
 * <p>只返回编码的话，前端得自己维护一份「编码 → 中文」的映射表；
 * 那份表一旦和后端不同步（比如后端加了新枚举值），界面就会出现空白或报错。
 * 由后端给出标签，前端不需要知道有多少种肌群。
 *
 * <p>代价是响应体积略大，但动作列表本来就不大（90 个内置动作，一页 20 条），
 * 这点开销换前端省事是值得的。
 *
 * <p><b>为什么不直接返回 {@code Exercise} 实体</b>：实体里有 {@code userId}
 * 和 {@code deleted} 这类内部字段，而且实体加字段时容易不小心外泄。
 * 用 DTO 逐字段列出允许外泄的内容是「白名单」思路。
 */
public record ExerciseResponse(

        Long id,
        String name,
        String alias,

        /** 主要肌群编码，如 {@code CHEST} */
        String primaryMuscle,
        /** 主要肌群中文标签，如「胸」 */
        String primaryMuscleLabel,

        /** 次要肌群，逗号分隔的编码 */
        String secondaryMuscles,

        String equipment,
        String equipmentLabel,

        String movementPattern,
        String movementPatternLabel,

        /** 计量类型编码。前端据此决定显示哪些输入控件 */
        String metricType,
        String metricTypeLabel,

        /** 自重动作的体重系数。负重动作为 null */
        BigDecimal bwFactor,

        /** 是否单侧动作。前端据此决定是否分左右录入 */
        Boolean unilateral,

        String instructions,
        String commonMistakes,

        /** 是否系统内置。前端据此决定「编辑/删除」按钮是否可点 */
        Boolean builtIn

) {

    /**
     * 从实体转换。
     *
     * <p>为空安全做了处理：枚举字段理论上不会为 null（建表时是 NOT NULL），
     * 但动作模式允许为空（有些动作确实不属于任何模式，比如耸肩），
     * 所以统一判空避免空指针。
     */
    public static ExerciseResponse from(Exercise e) {
        return new ExerciseResponse(
                e.getId(),
                e.getName(),
                e.getAlias(),
                e.getPrimaryMuscle() == null ? null : e.getPrimaryMuscle().name(),
                e.getPrimaryMuscle() == null ? null : e.getPrimaryMuscle().getDisplayName(),
                e.getSecondaryMuscles(),
                e.getEquipment() == null ? null : e.getEquipment().name(),
                e.getEquipment() == null ? null : e.getEquipment().getDisplayName(),
                e.getMovementPattern() == null ? null : e.getMovementPattern().name(),
                e.getMovementPattern() == null ? null : e.getMovementPattern().getDisplayName(),
                e.getMetricType() == null ? null : e.getMetricType().name(),
                e.getMetricType() == null ? null : e.getMetricType().getDisplayName(),
                e.getBwFactor(),
                // tinyint 在 Java 里是 Integer，转成 Boolean 让语义更清楚。
                // 前端拿到 true/false 比拿到 1/0 更直观。
                e.getIsUnilateral() != null && e.getIsUnilateral() == 1,
                e.getInstructions(),
                e.getCommonMistakes(),
                e.isBuiltIn()
        );
    }
}
