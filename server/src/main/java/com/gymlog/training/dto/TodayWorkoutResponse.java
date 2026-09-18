package com.gymlog.training.dto;

import com.gymlog.program.dto.ExpandedWorkout;
import com.gymlog.training.ScheduleState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 「今天练什么」的答案 —— App 首页那张卡片需要的全部数据。
 *
 * <h3>为什么训练日的内容直接复用 {@link ExpandedWorkout.ExerciseItem}</h3>
 *
 * <p>因为「今天练什么」和「第 N 周第 M 天练什么」本来就是同一个东西，
 * 只是前者多了一层「哪一天」的推断。重新定义一套展开结果的形状，
 * 会让跟练界面要写两套渲染逻辑，而且两边迟早会不一致。
 *
 * <h3>没有进行中的计划时</h3>
 *
 * <p><b>返回 200 且 {@link #programId()} 为 null</b>，而不是 404。
 *
 * <p>理由：首页加载时「我没有计划」是**正常状态**，不是错误。
 * 用 404 会让客户端把正常空状态和真正的请求错误混在一起处理。
 * 客户端判断 {@code programId == null} 就显示「选个模板开始吧」。
 */
public record TodayWorkoutResponse(

        /** 查询的是哪一天。客户端传了就原样带回，便于做「回看某天」 */
        LocalDate date,

        // ==================== 计划 ====================

        /** 进行中的计划 id。<b>null = 没有进行中的计划</b> */
        Long programId,
        String programName,

        /**
         * 计划自身的状态（ACTIVE / PAUSED）。
         *
         * <p>和 {@link #scheduleState()} 是两件事：前者是「用户对这个计划做了什么」，
         * 后者是「今天是计划的哪一天」。首页卡片需要前者来显示「已暂停」角标。
         *
         * <p>已归档的计划不会出现在这里——归档意味着不再使用。
         */
        String programStatus,
        String programStatusLabel,

        // ==================== 时间位置 ====================

        /** 第几周。已按总周数夹紧 */
        Integer weekNumber,
        /** 总周数。0 表示不限期 */
        Integer totalWeeks,
        ScheduleState scheduleState,
        String scheduleStateLabel,
        /** 还有几天开始。已开始或没填开始日期时为 null */
        Integer daysUntilStart,

        // ==================== 强度修饰 ====================

        /**
         * 本周是否减量周。
         *
         * <p>⚠️ 这两个字段描述的是**推荐的那个训练日**，不是「这周」本身。
         * 所以没有推荐训练日时（未开始 / 已结束 / 计划里没有训练日）
         * 它们会是 {@code false} / {@code null}，而 {@link #weekNumber()} 仍然有值。
         *
         * <p>这个区分是有意的：周次是**日历事实**，减量与否是**训练内容**。
         * 计划还没开始时告诉用户「本周 +0%」是噪声。
         */
        boolean deloadWeek,
        BigDecimal weightAdjustPct,

        // ==================== 训练日 ====================

        /** 该练第几个训练日（计划内的序号） */
        Integer dayNumber,
        String dayName,

        /**
         * 已完成的训练次数。
         *
         * <p>⚠️ <b>Phase 3 之前恒为 0</b>，见 {@code SessionCounter}。
         * 客户端可以据此显示「第 12 次训练」这类进度。
         */
        int completedSessions,

        /** 展开后的动作列表。{@code dayNumber} 为 null 时这里也是空 */
        List<ExpandedWorkout.ExerciseItem> exercises,

        /**
         * 计划里所有可选的训练日，供 M4-A-4「切换到本周其他训练日」使用。
         *
         * <p>一次带回，避免客户端为了显示切换列表再发一次请求。
         * 只带标识信息，不带完整动作列表——切换是低频操作，
         * 真切换时再拉那一天的详情。
         */
        List<DayOption> dayOptions

) {

    /** 可切换的训练日（仅标识信息） */
    public record DayOption(
            Integer dayNumber,
            String name,
            /** 动作数量。用于在切换列表里显示「推日 · 4 个动作」 */
            int exerciseCount,
            /** 是否是当前选中的那一天 */
            boolean current
    ) {
    }

    /** 没有进行中的计划 */
    public static TodayWorkoutResponse noProgram(LocalDate date) {
        return new TodayWorkoutResponse(
                date,
                null, null, null, null,
                null, null, null, null, null,
                false, null,
                null, null,
                0,
                List.of(), List.of());
    }
}
