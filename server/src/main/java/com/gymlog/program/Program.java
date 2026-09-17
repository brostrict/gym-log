package com.gymlog.program;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 训练计划，对应 {@code program} 表。
 *
 * <p><b>计划是纯私有数据</b>——不像动作库有内置的动作，
 * 计划只有用户自己创建的（从模板创建的本质是「复制一份模板」）。
 */
@Data
@TableName("program")
public class Program {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    /** 来源模板编码，NULL = 完全自定义 */
    private String templateCode;

    /** 总周数。0 表示不限期计划 */
    private Integer totalWeeks;

    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * 版本号。
     *
     * <p>修改**已开始**的计划时递增，并新建一条记录——
     * 原记录转为 {@link ProgramStatus#ARCHIVED}。
     * 历史训练记录指向旧版本，不受影响。
     */
    private Integer version;

    /**
     * 逻辑计划 id。同一计划的所有版本共享它。
     *
     * <p>第一个版本的 {@code rootId} 等于自己的 id。
     * 用于「查这个计划的所有版本」。
     */
    private Long rootId;

    private ProgramStatus status;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;

    // ==================================================================
    // 便捷判断
    // ==================================================================

    /** 是否不限期计划（没有固定的总周数） */
    public boolean isOpenEnded() {
        return totalWeeks == null || totalWeeks == 0;
    }

    /** 是否属于指定用户 */
    public boolean isOwnedBy(Long candidateUserId) {
        return userId != null && userId.equals(candidateUserId);
    }

    /** 是否可以修改（已归档/已完成的计划不该再改） */
    public boolean isEditable() {
        return status == ProgramStatus.ACTIVE || status == ProgramStatus.PAUSED;
    }
}
