package com.gymlog.training;

import lombok.Getter;

/**
 * 会话中某个动作的完成状态。
 */
@Getter
public enum SessionExerciseStatus {

    PENDING("未开始"),

    IN_PROGRESS("进行中"),

    COMPLETED("已完成"),

    /**
     * 临场跳过。
     *
     * <p><b>跳过也要留记录，不能直接删掉这个动作。</b>
     *
     * <p>因为训练总结里动作数量对不上时，用户分不清是
     * 「我当时跳过了」还是「计划里本来就没有」。
     * 留着并标记为跳过，总结可以说「完成 4/5 个动作（划船已跳过）」。
     */
    SKIPPED("已跳过");

    private final String displayName;

    SessionExerciseStatus(String displayName) {
        this.displayName = displayName;
    }
}
