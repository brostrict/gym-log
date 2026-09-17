package com.gymlog.program;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内置计划模板，对应 {@code program_template} 表。
 *
 * <p><b>结构存 JSON 而不是建关系表</b>——理由见 V8 迁移脚本的注释。
 * 简单说：模板只在「从模板创建计划」时被整体读取，
 * 从不被部分查询，所以关系表带来的查询能力用不上，
 * 却要付出「两套结构和两套增删改逻辑」的代价。
 *
 * <p><b>JSON 里用动作名称而不是 id</b>：动作 id 是自增的，
 * 各环境不一致。运行时按名称查 id。
 */
@Data
@TableName("program_template")
public class ProgramTemplate {

    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_UNPUBLISHED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 模板编码，如 {@code PPL_3DAY}。代码和接口引用它 */
    private String code;

    private String name;

    private String description;

    /** 适用目标：MUSCLE_GAIN / STRENGTH / FAT_LOSS / GENERAL */
    private String goal;

    /** 适用水平：BEGINNER / INTERMEDIATE / ADVANCED */
    private String level;

    private Integer sessionsPerWeek;

    private Integer totalWeeks;

    /** 训练日数（不含是否休息日的区分）。用于列表页展示 */
    private Integer daysPerWeek;

    private String equipmentSummary;

    private Integer estimatedMinutes;

    /**
     * 完整结构（JSON 字符串）。
     *
     * <p>用 String 接收而不是映射成对象——因为它的形状取决于
     * 业务需要，且只在「从模板创建计划」时被解析一次。
     * 映射成对象反而要维护一套额外的 DTO。
     */
    private String structure;

    private Integer sortOrder;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isPublished() {
        return status != null && status == STATUS_PUBLISHED;
    }
}
