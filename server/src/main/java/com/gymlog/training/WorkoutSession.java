package com.gymlog.training;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 训练会话，对应 {@code workout_session} 表。
 *
 * <p>一次训练。**整个项目的中心实体**——用户练了什么、练了多少，
 * 最终都落在这里（以及它下面的 {@link SessionExercise} 和 {@code set_record}）。
 *
 * <h3>⚠️ 这个实体里没有指向计划结构的任何外键</h3>
 *
 * <p>只有 {@code programId} 指向计划**本身**（而且计划走逻辑删除，不会消失）。
 * 训练日、动作、处方值全部存**快照**。
 *
 * <p>原因是计划结构编辑走全量替换，{@code day_template} /
 * {@code prescribed_exercise} 的 id 每次编辑都会变。
 * 引用它们的话，用户改一次计划，全部历史会话就指向了不存在的行——
 * 而且**不会有任何报错**，只是历史显示成空白。
 *
 * <p>详见 REQUIREMENTS 6.3 不变量 1、2 与 V11 迁移的文件头说明。
 */
@Data
@TableName("workout_session")
public class WorkoutSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 客户端幂等键（UUID）。
     *
     * <p><b>离线优先的必然要求</b>：健身房信号差，训练结束后重试上传是常态。
     * 没有它，重试 3 次就产生 3 条一模一样的训练记录（AC-5-1）。
     *
     * <p>NULL 表示不参与去重（手动补录不需要）。
     * 唯一索引 {@code (user_id, client_key)} 允许多个 NULL——
     * SQL 标准里 NULL ≠ NULL，所以没传 key 的会话不会互相冲突。
     */
    private String clientKey;

    /** 来源计划。NULL = 临时训练（不按计划，自由记录） */
    private Long programId;

    // ---------- 快照：这是哪一天 ----------

    private Integer dayNumber;

    /** 快照：训练日名称。计划里那个训练日后来改名或删除了，这里也不变 */
    private String dayName;

    // ---------- 快照：周期化位置 ----------

    private Integer weekNumber;

    private Integer isDeload;

    private BigDecimal weightAdjustPct;

    private SessionStatus status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /**
     * 实际训练时长（秒）。
     *
     * <p><b>刻意不用 {@code finishedAt - startedAt} 计算</b>：
     * 用户中途可能接电话、等器械，墙上时间不等于实际训练时间，
     * 而暂停了多久只有客户端知道。
     */
    private Integer durationSec;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer deleted;

    // ==================================================================
    // 便捷判断
    // ==================================================================

    public boolean isOwnedBy(Long candidateUserId) {
        return userId != null && userId.equals(candidateUserId);
    }

    /** 是否减量周训练 */
    public boolean isDeload() {
        return isDeload != null && isDeload == 1;
    }

    /** 是否还在进行中（可以继续记录） */
    public boolean isInProgress() {
        return status == SessionStatus.IN_PROGRESS;
    }

    /** 是否是临时训练（不来自计划） */
    public boolean isAdHoc() {
        return programId == null;
    }
}
