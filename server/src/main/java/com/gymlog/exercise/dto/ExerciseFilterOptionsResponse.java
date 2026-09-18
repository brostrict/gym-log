package com.gymlog.exercise.dto;

import com.gymlog.exercise.Equipment;
import com.gymlog.exercise.MetricType;
import com.gymlog.exercise.MovementPattern;
import com.gymlog.exercise.MuscleGroup;

import java.util.Arrays;
import java.util.List;

/**
 * 动作库的筛选项 —— 客户端据此生成筛选器。
 *
 * <h3>为什么要这个接口，而不是让客户端硬编码</h3>
 *
 * <p>和 {@code GET /body/metric-types} 是同一个理由：
 * 筛选项是**服务端也拥有的一份知识**——{@code ExerciseQuery} 按这些枚举值过滤，
 * 值不认识就直接查不到。客户端再写一份的话：
 *
 * <ul>
 *   <li>服务端加一个 {@code KETTLEBELL}，客户端筛选器里没有 → 用户<b>筛不到</b>
 *       「壶铃」这个选项，而动作库里明明有</li>
 *   <li>反过来客户端留着服务端已删的值 → 点了返回空列表，看起来像「没有这个动作」</li>
 * </ul>
 *
 * <p>两种失效都不报错。
 *
 * <h3>⚠️ {@code muscles} 用的是 {@code values()} 而不是 {@code muscles()}</h3>
 *
 * <p>这是**唯一**该用全量的地方：热身和拉伸虽然不是肌群，
 * 但用户要能在动作库里筛出它们。统计口径（肌群周组数）才排除它们——
 * 见 {@link MuscleGroup#isMuscle()}。
 */
public record ExerciseFilterOptionsResponse(

        List<Option> muscles,
        List<Option> equipment,
        List<Option> movementPatterns,
        List<Option> metricTypes

) {

    /** 一个筛选项：枚举名 + 中文名。客户端回传 {@code value}，显示 {@code label} */
    public record Option(String value, String label) {
    }

    public static ExerciseFilterOptionsResponse all() {
        return new ExerciseFilterOptionsResponse(
                Arrays.stream(MuscleGroup.values())
                        .map(m -> new Option(m.name(), m.getDisplayName())).toList(),
                Arrays.stream(Equipment.values())
                        .map(e -> new Option(e.name(), e.getDisplayName())).toList(),
                Arrays.stream(MovementPattern.values())
                        .map(m -> new Option(m.name(), m.getDisplayName())).toList(),
                Arrays.stream(MetricType.values())
                        .map(m -> new Option(m.name(), m.getDisplayName())).toList());
    }
}
