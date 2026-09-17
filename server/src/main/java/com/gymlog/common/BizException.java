package com.gymlog.common;

import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常。
 *
 * <p>用于表达「这是预期内的业务失败」，而不是「程序出 bug 了」。
 *
 * <p><b>什么时候抛它</b>：
 * <pre>
 * // ✅ 应该抛 BizException —— 这是业务规则，不是 bug
 * if (userMapper.existsByEmail(email)) {
 *     throw new BizException(ErrorCode.EMAIL_ALREADY_EXISTS);
 * }
 *
 * // ✅ 带上下文的自定义提示
 * if (weight &lt; 20 || weight &gt; 300) {
 *     throw new BizException(ErrorCode.BODY_METRIC_VALUE_OUT_OF_RANGE,
 *                            "体重需在 20–300kg 之间");
 * }
 *
 * // ❌ 不要这样 —— 用一个无意义的 RuntimeException 表达业务失败
 * throw new RuntimeException("邮箱已存在");
 * </pre>
 *
 * <p><b>为什么继承 RuntimeException 而不是 Exception</b>：
 * 受检异常（checked exception）会强制每一层调用都写 try-catch 或 throws，
 * Service 层会因为签名污染变得极难阅读。业务失败通过全局异常处理器
 * 统一兜住即可，不需要调用方逐层处理。
 * 这也是 Spring 官方推荐的实践——{@code @Transactional} 默认也只对
 * RuntimeException 回滚。
 *
 * <p><b>性能提示</b>：Java 的异常构造会填充栈信息（fillInStackTrace），
 * 是有开销的。业务失败如果非常频繁（比如每秒几千次），可以考虑覆盖
 * {@code fillInStackTrace()} 返回 this 来跳过。但在本项目的数据量级下
 * 完全不需要——不要过早优化。
 */
@Getter
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 对应的错误码，全局异常处理器据此决定 HTTP 状态与响应体 */
    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        // 把错误码的默认文案作为异常 message，便于日志里直接看出原因
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 带自定义提示文案。
     *
     * <p>注意：会一并传入 {@code cause} 的情况见下面的构造器。
     * 这个构造器用于「错误码是通用的，但提示要更具体」。
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 包装底层异常。
     *
     * <p>用在「捕获了一个技术异常，但对外要表达成业务错误」的场景。
     * 保留 cause 是为了日志里能看到完整调用链——
     * 对外只暴露 errorCode 的文案，对内保留全部细节。
     */
    public BizException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
