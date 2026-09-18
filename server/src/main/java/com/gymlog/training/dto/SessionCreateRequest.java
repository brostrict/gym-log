package com.gymlog.training.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 开始一次训练。
 *
 * <h3>两种模式，靠 {@code programId} 区分</h3>
 *
 * <pre>
 *   programId 有值  → 按计划训练：服务端展开处方，深拷贝成快照
 *   programId 为 null → 临时训练（M4-A-3）：建一个空会话，动作由客户端逐个加
 * </pre>
 *
 * <p><b>为什么「自动挑选计划」不在这里做</b>：
 * 客户端为了渲染首页，本来就已经调过 {@code GET /workouts/today}，
 * 那次调用已经把「当前在练哪个计划」算出来了。
 * 让它把结果带回来，比在这里再算一遍更好——
 * 只有一个地方决定「今天练什么」，不会出现首页显示 A 计划、
 * 点开始却进了 B 计划的情况。
 *
 * <p>如果将来有客户端想直接创建（比如从计划详情页点「开始训练」），
 * 它传的是明确的 programId，同样不需要服务端猜。
 */
public record SessionCreateRequest(

        /** 来源计划。null = 临时训练 */
        Long programId,

        /**
         * 训练日序号。null = 按轮转推荐（见 {@code TrainingSchedule}）
         *
         * <p>传了就用传的（M4-A-4「切换到本周其他训练日」）。
         */
        Integer dayNumber,

        /**
         * 按哪一天算。
         *
         * <p>null = 今天。<b>允许客户端指定是为了两件事</b>：
         * <ul>
         *   <li>补录：昨天练的今天才想起来记</li>
         *   <li>跨零点：23:55 打开首页、00:05 点开始，
         *       按「今天」算会算到下一周去，客户端把当时的日期带上就没问题</li>
         * </ul>
         */
        LocalDate date,

        /**
         * 幂等键（UUID）。<b>强烈建议客户端必传。</b>
         *
         * <p>离线优先下这是必需品：训练在飞机上/地下车库做完，
         * 联网后重试上传。没有它重试 3 次就产生 3 条训练记录。
         *
         * <p>服务端行为：key 已存在时**返回已存在的那条会话**，
         * 而不是报错——客户端重试本来就不该收到错误。
         *
         * <p>允许为 null（手动补录不需要幂等），但那样就没有防重保障。
         */
        @Size(max = 64, message = "幂等键不能超过 64 个字符")
        String clientKey,

        /**
         * 实际开始时刻。null = 现在。
         *
         * <p>由客户端提供，因为**离线训练时服务端不知道你什么时候开始的**。
         * 同步上来的时候可能已经是几小时后了。
         */
        LocalDateTime startedAt

) {
}
