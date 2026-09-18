package com.gymlog.training;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 「今天是计划的第几周、该练第几天」—— **纯函数**。
 *
 * <h3>核心规则：训练日按「练了几次」推进，不按日期推进</h3>
 *
 * <p>这条规则写在 V9 种子文件的头部注释里，这里实现它。举例
 * （2 个训练日模板 A、B，每周 3 练）：
 *
 * <pre>
 *   第 1 周：第1次→A  第2次→B  第3次→A
 *   第 2 周：第4次→B  第5次→A  第6次→B     ← 自动交替了
 * </pre>
 *
 * <p><b>为什么不能按日期算</b>：
 * <ul>
 *   <li>按「每周重置」会退化成 A/B/A 无限重复，5×5 的 A/B 交替就废了</li>
 *   <li>按「星期几固定」遇上出差、生病就崩——周三没练，
 *       周五该练的还是「周三那个」，而不是顺延</li>
 * </ul>
 *
 * <p>按次数推进的好处是**自愈**：漏练一次不会打乱顺序，
 * 只是整体往后挪。用户不需要「补课」，也不会莫名跳过某个训练日。
 *
 * <p>周次仍然由日期决定（{@code startDate + 7 × n}），因为
 * 周期化的强度调整本来就是按自然周走的——第 5 周该加重量，
 * 不会因为你少练两次就延后。
 *
 * <h3>为什么是纯函数</h3>
 *
 * <p>同 {@link com.gymlog.program.ProgramExpander}：没有依赖、没有状态、可单测。
 * 这里的边界情况不少（还没开始 / 刚好结束 / 超期很久 / 没填开始日期 /
 * 一次都没练过），全部值得单独测，而起 Spring 容器测这些太贵。
 */
public final class TrainingSchedule {

    private TrainingSchedule() {
    }

    /**
     * 算出今天在计划中的位置。
     *
     * @param startDate        计划开始日期。<b>允许为 null</b>——
     *                         很多计划只写「每周三练」，不填开始日期
     * @param totalWeeks       总周数。null 或 0 表示不限期
     * @param today            今天（由调用方传入，不用 {@code LocalDate.now()}，
     *                         否则测试就没法固定时间了）
     * @param trainingDayCount 训练日模板的数量（**不含休息日**）
     * @param completedSessions 该计划已完成的训练次数（含本次之前的所有）
     */
    public static Resolution resolve(LocalDate startDate,
                                     Integer totalWeeks,
                                     LocalDate today,
                                     int trainingDayCount,
                                     int completedSessions) {

        int effectiveTotalWeeks = (totalWeeks == null || totalWeeks <= 0) ? 0 : totalWeeks;

        // ---------- 时间状态与周次 ----------
        ScheduleState state;
        int weekNumber;
        Integer daysUntilStart = null;

        if (startDate == null) {
            // 没填开始日期：没有时间轴，永远停在第 1 周。
            // 这里**不报错**——「不填开始日期」是合法的用法
            // （长期维持的训练安排，本来就没有「第几周」的概念）。
            state = ScheduleState.ONGOING;
            weekNumber = 1;
        } else {
            long days = ChronoUnit.DAYS.between(startDate, today);

            if (days < 0) {
                state = ScheduleState.NOT_STARTED;
                weekNumber = 1;
                daysUntilStart = (int) -days;
            } else {
                int rawWeek = (int) (days / 7) + 1;

                if (effectiveTotalWeeks > 0 && rawWeek > effectiveTotalWeeks) {
                    state = ScheduleState.FINISHED;
                    // ⚠️ 夹到总周数，而不是让 weekNumber 一直涨。
                    //
                    // 超出之后继续涨的话，第 8 周的计划在第 20 周会去查
                    // 「第 20 周的强度修饰」——查不到，于是静默按基准值展开。
                    // 用户看到的是「计划还在继续」，实际上早就结束了。
                    weekNumber = effectiveTotalWeeks;
                } else {
                    state = ScheduleState.ONGOING;
                    weekNumber = rawWeek;
                }
            }
        }

        // ---------- 轮转到第几个训练日 ----------
        // 训练日数量为 0 时返回 -1（计划里没有可练的内容），
        // 让调用方明确处理，而不是返回 0 假装有个训练日
        Integer nextDayIndex = trainingDayCount <= 0
                ? null
                : Math.floorMod(completedSessions, trainingDayCount);

        return new Resolution(
                weekNumber,
                effectiveTotalWeeks,
                state,
                daysUntilStart,
                completedSessions,
                trainingDayCount,
                nextDayIndex
        );
    }

    /**
     * 计算结果。
     *
     * @param weekNumber        第几周（已按总周数夹紧，从 1 开始）
     * @param totalWeeks        总周数。0 表示不限期
     * @param state             时间状态
     * @param daysUntilStart    还有几天开始。已开始或没填日期时为 null
     * @param completedSessions 已完成的训练次数（原样带回，便于客户端展示进度）
     * @param trainingDayCount  训练日模板数量
     * @param nextDayIndex      下一个该练的训练日**下标**（从 0 开始）。
     *                          为 null 表示计划里没有训练日
     */
    public record Resolution(
            Integer weekNumber,
            Integer totalWeeks,
            ScheduleState state,
            Integer daysUntilStart,
            int completedSessions,
            int trainingDayCount,
            Integer nextDayIndex
    ) {
        /** 计划是否在正常进行中 */
        public boolean isOngoing() {
            return state == ScheduleState.ONGOING;
        }
    }
}
