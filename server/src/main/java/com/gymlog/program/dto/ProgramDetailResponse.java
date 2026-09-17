package com.gymlog.program.dto;

import com.gymlog.program.DayTemplate;
import com.gymlog.program.PrescribedExercise;
import com.gymlog.program.PrescribedSet;
import com.gymlog.program.Program;
import com.gymlog.program.WeekTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 计划详情 —— 返回完整嵌套结构。
 *
 * <p>结构与 {@link ProgramCreateRequest} 对称：创建时提交什么形状，
 * 查询时就返回什么形状。客户端可以用同一套模型序列化/反序列化。
 */
public record ProgramDetailResponse(

        Long id,
        String name,
        String description,
        Integer totalWeeks,
        LocalDate startDate,
        LocalDate endDate,

        String status,
        String statusLabel,

        Integer version,
        Long rootId,
        String templateCode,

        List<WeekItem> weeks,
        List<DayItem> days

) {

    /** 周结构（只有强度修饰，不含动作） */
    public record WeekItem(
            Integer weekNumber,
            Integer sessionsPerWeek,
            BigDecimal weightAdjustPct,
            Integer setAdjust,
            Boolean deload,
            String note
    ) {
        public static WeekItem from(WeekTemplate w) {
            return new WeekItem(
                    w.getWeekNumber(),
                    w.getSessionsPerWeek(),
                    w.getWeightAdjustPct(),
                    w.getSetAdjust(),
                    w.isDeloadWeek(),
                    w.getNote()
            );
        }
    }

    /** 训练日（含动作列表） */
    public record DayItem(
            Long id,
            Integer dayNumber,
            String name,
            Boolean restDay,
            String note,
            List<PrescriptionItem> exercises
    ) {
    }

    /**
     * 处方动作。
     *
     * <p><b>带上动作名称和肌群</b>，而不是只返回 {@code exerciseId}——
     * 否则客户端要为每个动作再查一次接口，一个训练日 8 个动作就是 8 次请求。
     *
     * <p>这是「**避免 N+1 查询**」的常见手段：与其让客户端循环调用，
     * 不如在服务端一次把关联信息带出来。这里的代价是 JOIN 或一次批量查询，
     * 远小于 N 次网络往返。
     */
    public record PrescriptionItem(
            Long id,
            Long exerciseId,
            String exerciseName,
            String primaryMuscle,
            String primaryMuscleLabel,
            String metricType,
            Integer orderIndex,

            /** 超级组编号。NULL = 普通动作 */
            Integer supersetGroup,
            Integer orderInGroup,

            Integer targetSets,
            Integer targetRepsMin,
            Integer targetRepsMax,
            Integer restSec,

            String targetWeightType,
            BigDecimal targetWeight,
            BigDecimal targetWeightPct,
            BigDecimal targetRpe,

            String note,

            /** 逐组处方。为空表示「每组都用上面的动作级默认值」 */
            List<SetItem> sets
    ) {
    }

    /** 逐组处方 */
    public record SetItem(
            Integer setNumber,
            String setType,
            String setTypeLabel,
            Integer targetReps,
            Integer targetRepsMin,
            Integer targetRepsMax,
            BigDecimal targetWeight,
            BigDecimal targetWeightPct,
            BigDecimal targetRpe,
            Integer restSec,
            String note
    ) {
        public static SetItem from(PrescribedSet s) {
            return new SetItem(
                    s.getSetNumber(),
                    s.getSetType() == null ? null : s.getSetType().name(),
                    s.getSetType() == null ? null : s.getSetType().getDisplayName(),
                    s.getTargetReps(),
                    s.getTargetRepsMin(),
                    s.getTargetRepsMax(),
                    s.getTargetWeight(),
                    s.getTargetWeightPct(),
                    s.getTargetRpe(),
                    s.getRestSec(),
                    s.getNote()
            );
        }
    }

    // ==================================================================
    // 组装
    // ==================================================================

    /**
     * 组装完整详情。
     *
     * <p><b>为什么用静态工厂接收「已经查好的各部分」而不是在方法里查库</b>：
     * DTO 的职责是数据转换，不该依赖 Mapper。
     * 查询编排放在 Service 里，DTO 只负责拼装——
     * 这样这个类可以脱离 Spring 容器单独测试。
     */
    public static ProgramDetailResponse assemble(Program program,
                                                 List<WeekTemplate> weeks,
                                                 List<DayItem> days) {
        return new ProgramDetailResponse(
                program.getId(),
                program.getName(),
                program.getDescription(),
                program.getTotalWeeks(),
                program.getStartDate(),
                program.getEndDate(),
                program.getStatus() == null ? null : program.getStatus().name(),
                program.getStatus() == null ? null : program.getStatus().getDisplayName(),
                program.getVersion(),
                program.getRootId(),
                program.getTemplateCode(),
                weeks.stream().map(WeekItem::from).toList(),
                days
        );
    }

    /** 从实体构造训练日条目（动作列表由 Service 填充） */
    public static DayItem dayFrom(DayTemplate d, List<PrescriptionItem> exercises) {
        return new DayItem(
                d.getId(),
                d.getDayNumber(),
                d.getName(),
                d.isRest(),
                d.getNote(),
                exercises
        );
    }

    /** 从实体构造处方条目（逐组处方由 Service 填充） */
    public static PrescriptionItem prescriptionFrom(PrescribedExercise p,
                                                    String exerciseName,
                                                    String primaryMuscle,
                                                    String primaryMuscleLabel,
                                                    String metricType,
                                                    List<SetItem> sets) {
        return new PrescriptionItem(
                p.getId(),
                p.getExerciseId(),
                exerciseName,
                primaryMuscle,
                primaryMuscleLabel,
                metricType,
                p.getOrderIndex(),
                p.getSupersetGroup(),
                p.getOrderInGroup(),
                p.getTargetSets(),
                p.getTargetRepsMin(),
                p.getTargetRepsMax(),
                p.getRestSec(),
                p.getTargetWeightType() == null ? null : p.getTargetWeightType().name(),
                p.getTargetWeight(),
                p.getTargetWeightPct(),
                p.getTargetRpe(),
                p.getNote(),
                sets
        );
    }
}
