package com.gymlog.program;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 训练日模板，对应 {@code day_template} 表。
 *
 * <p><b>「训练日」不等于「星期几」</b>——这是个容易混淆的地方。
 * 用户可能周三练推日、周六也练推日，或者某周只练两次。
 * 这里只定义「第几个练、练什么」，具体哪天练由用户的日程决定。
 *
 * <p>训练日**不含周次信息**——同一个「推日 A」在第 1 周和第 5 周结构相同，
 * 只是强度不同（由 {@link WeekTemplate} 的修饰决定）。
 */
@Data
@TableName("day_template")
public class DayTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long programId;

    /** 计划内的第几个训练日，从 1 开始 */
    private Integer dayNumber;

    private String name;

    /**
     * 是否休息日。
     *
     * <p>休息日**也占一个编号**，这样「第 3 天是休息」在计划里是明确的，
     * 而不是「没有第 3 天」。区别在于：
     * 前者说明用户主动安排了休息，后者看起来像计划缺了一块。
     */
    private Integer isRestDay;

    private String note;

    private LocalDateTime createdAt;

    public boolean isRest() {
        return isRestDay != null && isRestDay == 1;
    }
}
