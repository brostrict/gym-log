package com.gymlog.stats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 周粒度的序列工具 —— 纯函数，无依赖无状态。
 *
 * <h3>周起点是周一，不是周日</h3>
 *
 * <p>{@code METRICS 0.3}：「**周起点定为周一**，非周日。国内用户习惯。」
 *
 * <p>这条看着小，但错了会让整张图的 X 轴错位——用户看到「第 3 周」的数据里
 * 混着上一个周日的训练，而且不会有人发现。
 *
 * <h3>⚠️ 所有方法都不调 {@code LocalDate.now()}</h3>
 *
 * <p>{@code today} / {@code from} / {@code to} 一律作为**参数**传进来。
 * 这是沿用 {@link com.gymlog.training.TrainingSchedule} 的做法，它的类注释
 * 专门写了这条：「不用 {@code LocalDate.now()}，否则测试就没法固定时间了」。
 *
 * <p>{@code now()} 只允许出现在 Service 层（{@code TodayWorkoutService}
 * 就是在那里做 {@code date == null ? LocalDate.now() : date} 的）。
 */
public final class WeekSeries {

    private WeekSeries() {
    }

    /**
     * 某个日期所在周的**周一**。
     *
     * <p>用 {@link TemporalAdjusters#previousOrSame} 而不是
     * {@code date.with(DayOfWeek.MONDAY)}：后者依赖 {@code DAY_OF_WEEK}
     * 字段的语义（在 ISO 历法里恰好是周一为一周之始），而前者的意图
     * 是写在方法名里的，读代码的人不用去查历法。
     */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * 枚举 {@code [from, to]} 覆盖到的**每一个**周起点，升序。
     *
     * <p>两端都会先归到各自的周一，所以传入的日期在周中间也没关系。
     *
     * <p><b>为什么要枚举而不是「查出有数据的周」</b>：{@code METRICS 4.6} 要求
     * 「某周完全没练 → 显示为 **0 高度的空柱，不跳过**」——
     * 跳过空周会把「中断三周」画成「连续三周」，正好掩盖了最该看见的东西。
     *
     * <p>反过来，测量类序列（体重、e1RM）**不能**零填充（{@code METRICS 0.2}
     * 「缺失数据不插值」）——那是「那天没测」，不是「那天体重是 0」。
     * 区别在于：**计数类的 0 是真实测量值，测量类的 0 是编出来的**。
     */
    public static List<LocalDate> weekStarts(LocalDate from, LocalDate to) {
        LocalDate cursor = weekStart(from);
        LocalDate last = weekStart(to);
        if (cursor.isAfter(last)) {
            return List.of();
        }
        List<LocalDate> weeks = new ArrayList<>();
        while (!cursor.isAfter(last)) {
            weeks.add(cursor);
            cursor = cursor.plusWeeks(1);
        }
        return weeks;
    }

    /**
     * 连续训练周数（streak）：从当前周往回数，连续「每周至少训练 1 次」的周数。
     *
     * <p><b>这是 {@code METRICS 5.2} 的**简化版**，不是原文。</b>
     *
     * <p>原文写的是「连续满足『周训练次数 ≥ **计划频率**』的周数」，但那个定义
     * **在当前 schema 下算不出来**：计划频率存在 {@code week_template.sessions_per_week}，
     * 而计划结构编辑是**全量替换**（删了重建），没有历史；{@code workout_session}
     * 快照了 {@code week_number} / {@code is_deload}，唯独没有快照频率。
     * 更要命的是**没有训练的那一周没有任何会话行**，而那正是 streak 最关心的周。
     *
     * <p>所以退到不依赖任何计划配置的版本。它仍然回答 {@code METRICS 5.1} 的问句
     * （「我坚持得怎么样？最近有没有断？」），代价是门槛固定为 1 次而不是计划频率。
     * 文档已同步修改——{@code DEVELOPMENT-PLAN} 要求「实现偏离文档时当场改文档」。
     *
     * <h4>当前周怎么算（对 {@code METRICS 5.5} 的解释）</h4>
     *
     * <p>5.5 写「当前周不参与 streak 判定」，理由是「避免周一就显示断连」。
     * 按字面读，周三已经练了两次的人和不练的人显示同一个 streak——**丢了信息**，
     * 而且和它自己写的理由对不上。
     *
     * <p>所以按理由实现：**当前周练了就计入，没练也不算断**。两个目的都达到。
     *
     * @param sessionsByWeekStart 周起点 → 该周训练次数。缺键视为 0
     * @param today               今天（用于确定当前周）
     */
    public static int currentStreak(Map<LocalDate, Integer> sessionsByWeekStart, LocalDate today) {
        LocalDate thisWeek = weekStart(today);

        int streak = 0;
        // 当前周：练了就计入，没练也不断
        if (countOf(sessionsByWeekStart, thisWeek) > 0) {
            streak++;
        }
        // 已过完的周：必须每周都有训练，断一周就停
        LocalDate cursor = thisWeek.minusWeeks(1);
        while (countOf(sessionsByWeekStart, cursor) > 0) {
            streak++;
            cursor = cursor.minusWeeks(1);
        }
        return streak;
    }

    /**
     * 本周是否还没过完。
     *
     * <p>{@code METRICS 5.5}：当前周要用**半透明**显示——用户需要一眼看出
     * 「这周才周三，柱子矮是正常的」，否则会以为数据出了问题。
     */
    public static boolean isCurrentWeek(LocalDate weekStart, LocalDate today) {
        return weekStart.equals(weekStart(today));
    }

    private static int countOf(Map<LocalDate, Integer> map, LocalDate weekStart) {
        Integer n = map.get(weekStart);
        return n == null ? 0 : n;
    }
}
