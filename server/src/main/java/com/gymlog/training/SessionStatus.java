package com.gymlog.training;

import lombok.Getter;

/**
 * 训练会话的状态。
 */
@Getter
public enum SessionStatus {

    /**
     * 进行中。
     *
     * <p>断点续训的场景也停在这个状态——用户锁屏、切后台、甚至杀进程，
     * 只要没点「结束训练」，会话就还是进行中。
     */
    IN_PROGRESS("进行中"),

    /**
     * 已完成。
     *
     * <p><b>⚠️ 只有这个状态才推进训练日轮转。</b>
     *
     * <p>练到一半被叫走、或者发现状态不对提前收工，都该算 {@link #ABANDONED}——
     * 否则「跳过一天不会打乱轮转」这个设计就失效了：
     * 用户练了 1 个动作就放弃，轮转却往前推了一天，
     * 下次打开会发现该练的那天被跳过了。
     */
    COMPLETED("已完成"),

    /**
     * 中途放弃。
     *
     * <p>和「删除」的区别：放弃的记录**仍然保留**，
     * 计入历史（用户能看到「那天我只练了 10 分钟」），
     * 但不计入完成率和轮转。
     */
    ABANDONED("已放弃");

    private final String displayName;

    SessionStatus(String displayName) {
        this.displayName = displayName;
    }
}
