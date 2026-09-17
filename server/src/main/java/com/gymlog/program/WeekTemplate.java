package com.gymlog.program;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 周模板，对应 {@code week_template} 表。
 *
 * <p>描述「第 N 周相对基准强度怎么调」，是**周期化的载体**。
 *
 * <p>处方值的展开公式：
 * <pre>
 *   最终重量 = 动作级基准重量 × (1 + weightAdjustPct / 100)
 *   最终组数 = 动作级基准组数 + setAdjust
 * </pre>
 *
 * <p><b>为什么这样拆</b>：8 周 × 每周 3 练 = 24 个训练日。
 * 不拆的话要建 24 个训练日模板，改一个动作要改 8 遍。
 * 拆开后只需 3 个训练日结构 + 8 个强度修饰。
 */
@Data
@TableName("week_template")
public class WeekTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long programId;

    /** 第几周，从 1 开始 */
    private Integer weekNumber;

    private Integer sessionsPerWeek;

    /** 重量调整百分比。deload 用负数，如 -40 表示降到 60% */
    private BigDecimal weightAdjustPct;

    /** 组数调整，可正可负 */
    private Integer setAdjust;

    /**
     * 是否减量周。
     *
     * <p><b>这个标记会被统计逻辑用到</b>：计算计划完成率时 deload 周要排除，
     * 否则用户按计划减量反而被算成「没完成」。
     */
    private Integer isDeload;

    private String note;

    private LocalDateTime createdAt;

    public boolean isDeloadWeek() {
        return isDeload != null && isDeload == 1;
    }
}
