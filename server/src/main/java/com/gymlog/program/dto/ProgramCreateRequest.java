package com.gymlog.program.dto;

import com.gymlog.program.TargetWeightType;
import com.gymlog.training.SetType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 创建计划的请求 —— **一次提交完整结构**。
 *
 * <p><b>为什么不做成「先建计划，再逐个加训练日」的多个接口</b>：
 * <ol>
 *   <li><b>原子性</b>：多次请求意味着中间状态可见。
 *       用户建了计划却还没加动作时，App 上会出现一个空计划。</li>
 *   <li><b>性能</b>：一个 8 周计划有 5 层、上百条记录。
 *       分成几十个请求，网络往返就占了大半时间。</li>
 *   <li><b>简化客户端</b>：从模板创建计划时，模板本来就是一份完整结构，
 *       拆开再拼装纯属自找麻烦。</li>
 * </ol>
 *
 * <p>代价是请求体较大，但计划创建是**低频操作**（一个人几个月才建一次计划），
 * 这点体积完全不是问题。
 *
 * <p><b>用嵌套 record 表达层级</b>：结构和数据库的层级一一对应，
 * 读代码时能直接看出「计划里有哪些周、每周有哪些训练日」。
 */
public record ProgramCreateRequest(

        @NotBlank(message = "计划名称不能为空")
        @Size(max = 64, message = "计划名称不能超过 64 个字符")
        String name,

        @Size(max = 500, message = "计划说明不能超过 500 个字符")
        String description,

        /**
         * 总周数。
         *
         * <p><b>0 或 null 表示不限期计划</b>——持续进行，没有结束日期。
         * 这是很多人的真实用法：长期维持的训练安排，没有「第 8 周结束」这种概念。
         */
        @Min(value = 0, message = "总周数不能为负")
        @Max(value = 104, message = "总周数不能超过 104 周（两年）")
        Integer totalWeeks,

        LocalDate startDate,

        /** 周结构。不限期计划也需要——它定义的是「每周怎么循环」 */
        @Valid
        List<WeekRequest> weeks,

        @Valid
        List<DayRequest> days

) {

    /**
     * 周结构。
     *
     * <p>注意这里只有**强度修饰**，没有具体动作——
     * 动作在 {@link DayRequest} 里，与周次无关。
     */
    public record WeekRequest(

            @NotNull(message = "周序号不能为空")
            @Min(value = 1, message = "周序号从 1 开始")
            Integer weekNumber,

            @Min(value = 1, message = "每周至少练 1 次")
            @Max(value = 7, message = "每周最多练 7 次")
            Integer sessionsPerWeek,

            /**
             * 重量调整百分比。
             *
             * <p>范围限制在 −50% 到 +30%：
             * <ul>
             *   <li>低于 −50% 已经不是训练了</li>
             *   <li>高于 +30% 增幅过大，容易受伤</li>
             * </ul>
             * 这个范围限制是**安全兜底**，不是技术限制。
             */
            @DecimalMin(value = "-50.0", message = "减量幅度不能超过 50%")
            @DecimalMax(value = "30.0", message = "增量幅度不能超过 30%")
            BigDecimal weightAdjustPct,

            @Min(value = -5, message = "组数调整不能低于 -5")
            @Max(value = 5, message = "组数调整不能超过 +5")
            Integer setAdjust,

            Boolean isDeload,

            @Size(max = 255)
            String note

    ) {
    }

    /**
     * 训练日。
     *
     * <p><b>「训练日」不等于「星期几」</b>——只定义「第几个练、练什么」。
     */
    public record DayRequest(

            @NotNull(message = "训练日序号不能为空")
            @Min(value = 1, message = "训练日序号从 1 开始")
            Integer dayNumber,

            @NotBlank(message = "训练日名称不能为空")
            @Size(max = 64, message = "训练日名称不能超过 64 个字符")
            String name,

            Boolean isRestDay,

            @Size(max = 255)
            String note,

            /** 休息日不需要动作 */
            @Valid
            List<PrescriptionRequest> exercises

    ) {
    }

    /**
     * 处方动作。
     *
     * <p><b>注意没有 {@code exerciseId} 的归属校验</b>——
     * 那在 Service 层做。用户可能传一个别人的自定义动作 id，
     * 必须在插入前校验可见性。
     */
    public record PrescriptionRequest(

            @NotNull(message = "请选择动作")
            Long exerciseId,

            @NotNull(message = "顺序不能为空")
            @Min(value = 0)
            Integer orderIndex,

            /** 超级组编号。NULL = 普通动作 */
            @Min(value = 1, message = "超级组编号从 1 开始")
            Integer supersetGroup,

            /** 组内顺序，从 1 开始 */
            @Min(value = 1, message = "组内顺序从 1 开始")
            Integer orderInGroup,

            @NotNull(message = "目标组数不能为空")
            @Min(value = 1, message = "至少 1 组")
            @Max(value = 20, message = "单个动作最多 20 组")
            Integer targetSets,

            @Min(value = 1, message = "次数至少为 1")
            @Max(value = 100, message = "次数不能超过 100")
            Integer targetRepsMin,

            @Min(value = 1, message = "次数至少为 1")
            @Max(value = 100, message = "次数不能超过 100")
            Integer targetRepsMax,

            /**
             * 组间休息秒数。
             *
             * <p>上限 600 秒（10 分钟）——力量训练最长也就 5 分钟，
             * 超过 10 分钟多半是填错了。
             */
            @NotNull(message = "组间休息不能为空")
            @Min(value = 0, message = "休息时间不能为负")
            @Max(value = 600, message = "组间休息不能超过 600 秒")
            Integer restSec,

            @NotNull(message = "请选择目标重量类型")
            TargetWeightType targetWeightType,

            @DecimalMin(value = "0.0", message = "重量不能为负")
            @DecimalMax(value = "1000.0", message = "重量超出合理范围")
            BigDecimal targetWeight,

            @DecimalMin(value = "1.0", message = "%1RM 至少为 1")
            @DecimalMax(value = "150.0", message = "%1RM 不能超过 150")
            BigDecimal targetWeightPct,

            @DecimalMin(value = "1.0", message = "RPE 最小为 1")
            @DecimalMax(value = "10.0", message = "RPE 最大为 10")
            BigDecimal targetRpe,

            /**
             * 目标持续时长（秒）。仅时长类动作（平板支撑等）需要。
             *
             * <p>不给 {@code @Min(1)} 之外的约束的话，客户端可以传 0 或负数，
             * 那样倒计时一开始就是负数——不报错，但跟练页会立刻显示「已超时」。
             */
            @Min(value = 1, message = "目标时长至少 1 秒")
            @Max(value = 3600, message = "目标时长最多 3600 秒")
            Integer targetDurationSec,

            /**
             * 倒计时期间的播报间隔（秒）。{@code 0} = 不间隔播报。
             *
             * <p>注意它**没有**「必须小于 targetDurationSec」的约束：
             * 间隔比目标还长时，倒计时期间一次都不响，只在超时期间响——
             * 这是个合法选择（「撑过目标之后再提醒我」），不该被拦。
             */
            @Min(value = 0, message = "播报间隔不能为负")
            @Max(value = 300, message = "播报间隔最多 300 秒")
            Integer announceIntervalSec,

            @Size(max = 255)
            String note,

            /**
             * 逐组处方。
             *
             * <p><b>可以为空</b>——「3组×10次」这种统一目标用动作级默认值即可。
             * 只有递增/递减/金字塔组才需要建这里。
             */
            @Valid
            List<SetRequest> sets

    ) {
    }

    /** 逐组处方。只为需要特殊目标的组建记录 */
    public record SetRequest(

            @NotNull(message = "组序号不能为空")
            @Min(value = 1, message = "组序号从 1 开始")
            Integer setNumber,

            SetType setType,

            @Min(value = 1) @Max(value = 100)
            Integer targetReps,

            @Min(value = 1) @Max(value = 100)
            Integer targetRepsMin,

            @Min(value = 1) @Max(value = 100)
            Integer targetRepsMax,

            @DecimalMin(value = "0.0") @DecimalMax(value = "1000.0")
            BigDecimal targetWeight,

            @DecimalMin(value = "1.0") @DecimalMax(value = "150.0")
            BigDecimal targetWeightPct,

            @DecimalMin(value = "1.0") @DecimalMax(value = "10.0")
            BigDecimal targetRpe,

            @Min(value = 0) @Max(value = 600)
            Integer restSec,

            @Size(max = 255)
            String note

    ) {
    }
}
