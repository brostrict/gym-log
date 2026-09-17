package com.gymlog.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体。
 *
 * <p>所有接口——无论成功还是失败——都返回这个结构：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "成功",
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p><b>为什么必须统一</b>：前端只需要写一次解析逻辑。
 * <pre>
 * // 前端代码可以这么写
 * const res = await api.getUser(id)
 * if (res.code === 0) {
 *   render(res.data)
 * } else {
 *   toast(res.message)
 * }
 * </pre>
 * 如果成功返回 {@code {...}} 而失败返回 {@code {error: ..., trace: ...}}，
 * 前端每个接口都要写两套判断，且字段名还不一致。
 *
 * <p><b>不可变设计</b>：字段全是 final，只能通过静态工厂方法创建。
 * 响应对象一旦构造就不该被修改——避免在传递过程中被某处意外改掉。
 *
 * @param <T> 业务数据类型。无数据时用 {@link Void}
 */
@Getter
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务错误码，0 表示成功。前端依据它做分支判断 */
    private final int code;

    /** 提示文案，可直接展示给用户 */
    private final String message;

    /** 业务数据。失败时为 null */
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 成功 ====================

    /** 成功，无返回数据。用于删除、修改这类只需要知道成败的接口 */
    public static <T> Result<T> ok() {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    /** 成功，带数据 */
    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    /** 成功，带数据和自定义提示 */
    public static <T> Result<T> ok(T data, String message) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    // ==================== 失败 ====================

    /** 失败，使用错误码自带的提示文案 */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败，覆盖提示文案。
     *
     * <p>用在需要补充上下文时——例如 {@code BAD_REQUEST} 是通用码，
     * 但具体可以提示「体重必须在 20–300kg 之间」。
     */
    public static <T> Result<T> fail(ErrorCode errorCode, String message) {
        return new Result<>(errorCode.getCode(), message, null);
    }

    // ==================== 辅助 ====================

    /**
     * 是否成功。
     *
     * <p>仅供服务端代码（如测试断言、内部判断）使用。
     * 标 {@link JsonIgnore} 是因为它和 {@code code == 0} 完全等价，
     * 返回给前端属于冗余字段。
     */
    @JsonIgnore
    public boolean isSuccess() {
        return this.code == ErrorCode.SUCCESS.getCode();
    }
}
