package com.gymlog.training;

/**
 * 计划相对今天的时间状态。
 *
 * <p><b>为什么不复用 {@link com.gymlog.program.ProgramStatus}</b>：
 * 两者回答的是不同的问题。
 * <pre>
 *   ProgramStatus  = 用户对这个计划做了什么（进行中 / 已暂停 / 已归档）
 *   ScheduleState  = 今天是计划的哪一天（还没开始 / 进行中 / 已结束）
 * </pre>
 *
 * <p>一个 {@code ACTIVE} 的计划完全可能还没到开始日期，或者已经过了总周数。
 * 把这两件事塞进一个枚举，客户端就不得不靠「状态 + 日期」自己推断，
 * 而这段推断逻辑在 Flutter 和 Vue 里要各写一遍。
 */
public enum ScheduleState {

    /** 还没到开始日期 */
    NOT_STARTED("未开始"),

    /** 在计划周期内。不限期的计划永远是它 */
    ONGOING("进行中"),

    /** 已经超过总周数。不限期计划不会出现这个状态 */
    FINISHED("已结束");

    private final String displayName;

    ScheduleState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
