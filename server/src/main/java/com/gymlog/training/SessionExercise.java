package com.gymlog.training;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会话动作快照，对应 {@code session_exercise} 表。
 *
 * <p>创建会话时从 {@code prescribed_exercise} **深拷贝**而来。
 * 此后用户改计划，这里的内容不变（不变量 1）。
 *
 * <h3>为什么同时存 exerciseId 和 exerciseName</h3>
 *
 * <p>两列看着冗余，其实各有用途，<b>缺一不可</b>：
 *
 * <pre>
 *   exerciseId   用于聚合 ——「单动作历史 + sparkline」要按动作分组。
 *                但它是**尽力而为**的：用户删掉自定义动作后就查不到了。
 *
 *   exerciseName **显示用的真相** —— 动作被删了、改名了，
 *                历史记录里显示的仍然是当时那个名字。
 * </pre>
 *
 * <ul>
 *   <li>只存 id：动作一删，历史就变成「未知动作」</li>
 *   <li>只存 name：没法按动作聚合，sparkline 做不出来</li>
 * </ul>
 *
 * <p>这和 {@code prescribed_exercise} 的情况不同——那边删动作会被
 * {@code EXERCISE_IN_USE} 挡住（动作还被计划引用着）。
 * 但历史会话不该受这个约束：**练过的记录不能因为动作下架就丢失**。
 */
@Data
@TableName("session_exercise")
public class SessionExercise {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** 动作 id，用于聚合。动作被删除后这个值可能查不到对应记录 */
    private Long exerciseId;

    /** 快照：动作名称。显示用，永不改变 */
    private String exerciseName;

    /** 快照：主要肌群，用于肌群容量与组数统计 */
    private String primaryMuscle;

    /** 快照：计量类型，决定跟练界面显示哪些录入控件 */
    private String metricType;

    /**
     * 快照：自重系数。
     *
     * <p>自重动作的容量 = {@code 体重 × bwFactor × 次数}（AC-7-8）。
     *
     * <p><b>⚠️ 必须快照，不能查询时去动作库现取。</b>
     * 因为 {@code bw_factor} 是**管理员可编辑的**（M10-B-3）——
     * 管理员把引体的系数从 1.00 改成 0.95，
     * 用户三个月前的历史容量会追溯性地变小，而且不报任何错。
     *
     * <p>NULL 表示该动作不是自重动作（负重类动作不需要系数）。
     */
    private BigDecimal bwFactor;

    private Integer orderIndex;

    // ---------- 超级组（不变量 4：分组在快照中固化）----------

    private Integer supersetGroup;

    private Integer orderInGroup;

    /**
     * 计划组数。
     *
     * <p>⚠️ 与 {@code session_set_target} 的行数冗余，这是**刻意的反规范化**：
     * 跟练界面每屏都要显示「第 2 组 / 共 5 组」，
     * 为了这个分母单独查一次子表不划算。
     *
     * <p>快照不可变，所以这份冗余没有「两处不一致」的风险。
     */
    private Integer targetSets;

    private String note;

    private SessionExerciseStatus status;

    private LocalDateTime createdAt;

    // ==================================================================
    // 便捷判断
    // ==================================================================

    public boolean isInSuperset() {
        return supersetGroup != null;
    }

    /** 是否已结束（完成或跳过）——两种都不再需要用户操作 */
    public boolean isFinished() {
        return status == SessionExerciseStatus.COMPLETED
                || status == SessionExerciseStatus.SKIPPED;
    }
}
