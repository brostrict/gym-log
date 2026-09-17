package com.gymlog.exercise.dto;

import com.gymlog.exercise.Equipment;
import com.gymlog.exercise.MetricType;
import com.gymlog.exercise.MovementPattern;
import com.gymlog.exercise.MuscleGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建 / 编辑自定义动作的请求。
 *
 * <p><b>为什么创建和编辑共用一个 DTO</b>：字段完全一样。
 * 分成两个类只会带来重复——改一个字段要同步改两处，
 * 而它们本该保持一致。
 *
 * <p>（如果将来编辑允许「只改部分字段」，那时再拆成
 * {@code CreateRequest} + {@code PatchRequest}。）
 *
 * <p><b>注意这里没有 {@code userId} 字段</b>：归属由服务端从 token 决定，
 * **绝不能由客户端指定**。否则用户可以伪造 {@code userId: 0}
 * 去创建「内置动作」，或把动作挂到别人名下。
 *
 * <p>同样没有 {@code status}、{@code deleted}、{@code builtIn}——
 * 这些都是服务端控制的字段。
 */
@Data
public class ExerciseSaveRequest {

    @NotBlank(message = "动作名称不能为空")
    @Size(max = 64, message = "动作名称不能超过 64 个字符")
    private String name;

    @Size(max = 255, message = "别名不能超过 255 个字符")
    private String alias;

    @NotNull(message = "请选择主要肌群")
    private MuscleGroup primaryMuscle;

    @Size(max = 255, message = "次要肌群不能超过 255 个字符")
    private String secondaryMuscles;

    @NotNull(message = "请选择器械")
    private Equipment equipment;

    /** 动作模式允许为空——有些动作确实不属于任何模式（如耸肩、腕弯举） */
    private MovementPattern movementPattern;

    @NotNull(message = "请选择计量类型")
    private MetricType metricType;

    /**
     * 自重动作的体重系数。
     *
     * <p>不在这里做范围校验——具体规则（什么时候必须有值、
     * 什么时候必须为空）依赖 {@link #metricType}，
     * 属于**跨字段的业务规则**，注解表达不了，放在 Service 里处理。
     */
    private BigDecimal bwFactor;

    @Size(max = 2000, message = "动作要领不能超过 2000 个字符")
    private String instructions;

    @Size(max = 2000, message = "常见错误不能超过 2000 个字符")
    private String commonMistakes;

    /** 是否单侧动作。不传按 false 处理 */
    private Boolean unilateral;
}
