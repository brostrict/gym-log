package com.gymlog.training;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 统计某个计划已完成的训练次数 —— **Phase 3 的临时接缝**。
 *
 * <h3>⚠️ 这个类现在是假的</h3>
 *
 * <p>{@code workout_session} 表要到 Phase 3 才建，所以这里恒返回 0。
 *
 * <p><b>后果</b>：训练日轮转永远停在第 1 个训练日，
 * 也就是「今天练什么」每次都推荐同一个训练日。
 *
 * <p>这不是 bug，是**已知的、临时的**缺口——但必须显式写出来，
 * 否则下一个人（包括三个月后的我自己）会以为轮转逻辑坏了。
 *
 * <h3>为什么值得单独开一个类，而不是在 Service 里写个 0</h3>
 *
 * <p>因为这样 Phase 3 要改的地方**只有这一个文件的一个方法**。
 * 如果写在 Service 里，轮转的调用点、参数拼装、注释混在一起，
 * 将来很容易改漏或者改错位置。
 *
 * <p>而且这个类名本身就在说明「这个数字应该从会话表来」——
 * 一个孤零零的 {@code 0} 常量做不到这一点。
 *
 * <h3>Phase 3 要做的</h3>
 * <pre>
 *   SELECT COUNT(*) FROM workout_session
 *    WHERE user_id = ? AND program_id = ? AND status = 'COMPLETED'
 * </pre>
 *
 * <p><b>只数 COMPLETED</b>：开始了一半就放弃的训练不该推进轮转。
 * 用户练到一半被叫走，下次应该还是练同一个训练日。
 *
 * <p>还要注意 <b>跨周期</b>：轮转是整个计划周期内连续的，
 * 不是每周重置（见 V9 种子文件头部注释）。
 * 所以这里**不加日期范围条件**，数的是全部历史。
 */
@Slf4j
@Component
public class SessionCounter {

    /**
     * 该计划已完成的训练次数。
     *
     * @return 目前恒为 0（会话表尚未建立）
     */
    public int completedCount(Long userId, Long programId) {
        // Phase 3 替换这里。
        //
        // 保留这行日志：等会话功能上线后，
        // 如果这里还在打日志，说明忘了替换实现。
        log.debug("SessionCounter 仍是临时实现，恒返回 0 | userId={} | programId={}",
                userId, programId);
        return 0;
    }
}
