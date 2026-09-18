package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 统计某个计划已完成的训练次数。
 *
 * <h3>用途</h3>
 *
 * <p>训练日轮转的输入（见 {@link TrainingSchedule}）：
 * <pre>
 *   第 N 次训练 → 练第 (N mod 训练日数) 个训练日
 * </pre>
 *
 * <p>按次数推进而不是按日期，是「漏练一次不打乱顺序」的关键。
 *
 * <h3>为什么只数 COMPLETED</h3>
 *
 * <p>练到一半放弃的不该推进轮转。用户练了 1 个动作被叫走，
 * 下次打开应该还是练同一个训练日——否则他会发现某个训练日被跳过了。
 *
 * <h3>为什么没有日期范围条件</h3>
 *
 * <p>轮转是**整个计划周期内连续**的，不是每周重置。
 * 2 个训练日 + 每周 3 练时：
 * <pre>
 *   第 1 周：第1次→A  第2次→B  第3次→A
 *   第 2 周：第4次→B  第5次→A  第6次→B     ← 自动交替
 * </pre>
 * 加上「本周」的范围条件就会退化成 A/B/A 无限重复，
 * 5×5 的 A/B 交替直接废掉。规则详见 V9 种子文件头部。
 *
 * <h3>⚠️ 这个类曾经是假的</h3>
 *
 * <p>步骤 2.15 实现「今天练什么」时，会话表还没建，
 * 所以这里曾经恒返回 0，导致轮转永远停在第 1 个训练日。
 * 那是个**显式记录的临时接缝**，现在补上了。
 */
@Component
@RequiredArgsConstructor
public class SessionCounter {

    private final WorkoutSessionMapper sessionMapper;

    /**
     * 该计划已完成的训练次数。
     *
     * @param programId 计划 id。<b>为 null 时返回 0</b>——
     *                  临时训练不属于任何计划，不该影响任何计划的轮转
     * @return 已完成的训练场次
     */
    public int completedCount(Long userId, Long programId) {
        if (programId == null) {
            return 0;
        }
        Long count = sessionMapper.selectCount(
                new LambdaQueryWrapper<WorkoutSession>()
                        .eq(WorkoutSession::getUserId, userId)
                        .eq(WorkoutSession::getProgramId, programId)
                        .eq(WorkoutSession::getStatus, SessionStatus.COMPLETED));
        return count == null ? 0 : count.intValue();
    }
}
