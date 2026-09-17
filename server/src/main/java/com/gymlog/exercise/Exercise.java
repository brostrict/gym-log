package com.gymlog.exercise;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 动作，对应 {@code exercise} 表。
 *
 * <p><b>枚举字段怎么映射到数据库</b>：MyBatis 默认用 {@code EnumTypeHandler}，
 * 把枚举按 {@code name()} 存成字符串（{@code MetricType.WEIGHT_REPS}
 * ↔ 数据库里的 {@code "WEIGHT_REPS"}），读取时用 {@code Enum.valueOf()} 还原。
 * 所以不需要任何额外配置。
 *
 * <p>⚠️ 这意味着**枚举常量名就是数据库里的值**，改名等于改数据。
 * 要改显示名称请改 {@code displayName} 字段，不要改常量名。
 */
@Data
@TableName("exercise")
public class Exercise {

    /**
     * 内置动作的 {@code userId} 哨兵值。
     *
     * <p>为什么不用 {@code null}：唯一索引 {@code uk_exercise_user_name}
     * 依赖这一列，而 SQL 标准规定 NULL 不等于 NULL——
     * 唯一索引里多个 NULL 被视为互不相同，约束会失效。
     * 详见 V4 迁移脚本里的说明。
     */
    public static final long BUILT_IN_USER_ID = 0L;

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用（下架，但历史记录仍可正常显示） */
    public static final int STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 0 = 内置动作；其他 = 创建者用户 id */
    private Long userId;

    private String name;

    /** 别名，逗号分隔，用于搜索匹配 */
    private String alias;

    private MuscleGroup primaryMuscle;

    /** 次要肌群，逗号分隔。V1 不参与统计，仅展示 */
    private String secondaryMuscles;

    private Equipment equipment;

    private MovementPattern movementPattern;

    /** 计量类型，决定组记录里哪些字段有意义 */
    private MetricType metricType;

    /** 自重动作的体重系数。仅 {@code REPS_ONLY} 类需要，负重动作留 null */
    private BigDecimal bwFactor;

    /** 是否单侧动作：1=是，0=否 */
    private Integer isUnilateral;

    private String instructions;

    private String commonMistakes;

    /** 状态：1=启用，0=停用 */
    private Integer status;

    private Integer sortOrder;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;

    // ==================================================================
    // 便捷判断
    // ==================================================================

    /** 是否系统内置动作（决定谁能修改它） */
    public boolean isBuiltIn() {
        return userId != null && userId == BUILT_IN_USER_ID;
    }

    /** 是否属于指定用户（用户只能改自己的自定义动作） */
    public boolean isOwnedBy(Long candidateUserId) {
        return userId != null && candidateUserId != null && userId.equals(candidateUserId);
    }

    /** 是否可用（启用且未删除） */
    public boolean isAvailable() {
        return status != null && status == STATUS_ENABLED
                && (deleted == null || deleted == 0);
    }
}
