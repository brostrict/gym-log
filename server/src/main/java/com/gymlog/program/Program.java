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
     * 结构版本号，从 1 开始。**每次编辑计划结构递增**。
     *
     * <p><b>⚠️ 这个字段的用途变过，别按旧注释理解。</b>
     *
     * <p><b>原设计</b>：修改已开始的计划 = 归档旧记录 + 新建一条新记录，
     * 历史训练记录指向旧版本。即「版本化」，`rootId` 用来串联同一计划的各版本。
     *
     * <p><b>现在</b>：版本化已降级（理由见 REQUIREMENTS 6.3 不变量 2——
     * 会话快照已经把历史数据的风险全挡掉了，版本化只剩「回滚」这点价值）。
     * 结构编辑**直接改原记录**，这个字段改用作**乐观锁的计数器**：
     * <pre>
     *   客户端 GET 详情 → 拿到 version = 3
     *   客户端 PUT 结构 → 带上 expectedVersion = 3
     *   服务端比对：现在是 3 就写入并升到 4；不是 3 就返回 409
     * </pre>
     *
     * <p>只有**结构**变化才递增。改名称、改说明、暂停/恢复都不动它——
     * 它标记的是「结构变了几次」，不是「记录改了几次」。
     * 否则另一台设备只是在改名字，就会让正在编辑结构的设备莫名冲突。
     *
     * <p>{@code rootId} 目前没有用途，保留是为了将来真要做版本化时不用改表。
     */
    private Integer version;

    /** 逻辑计划 id。原为版本化设计保留，当前所有记录的 {@code rootId} 都等于自己的 id */
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
