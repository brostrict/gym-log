package com.gymlog.program;

import lombok.Getter;

/**
 * 计划状态。
 *
 * <p><b>为什么需要 {@code ARCHIVED} 而不只是「删除」</b>：
 * 用户修改一个进行中的计划时，旧版本不是被删掉，而是标记为归档。
 * 历史训练记录指向的是旧版本，删了就查不出「我当时按什么计划练的」。
 */
@Getter
public enum ProgramStatus {

    /** 进行中 */
    ACTIVE("进行中"),

    /**
     * 已暂停。
     *
     * <p><b>暂停期间不计入完成率的分母</b>——
     * 用户受伤休息两周，不该因此被算成「计划完成度只有 60%」。
     * 见 REQUIREMENTS M7 的完成率定义。
     */
    PAUSED("已暂停"),

    /** 已归档：被新版本取代，历史记录仍指向它 */
    ARCHIVED("已归档"),

    /** 已完成：走完了全部周数 */
    FINISHED("已完成");

    private final String displayName;

    ProgramStatus(String displayName) {
        this.displayName = displayName;
    }

    /** 是否是可以执行训练的状态 */
    public boolean isExecutable() {
        return this == ACTIVE;
    }
}
