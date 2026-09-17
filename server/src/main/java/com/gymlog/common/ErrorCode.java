package com.gymlog.common;

import org.springframework.http.HttpStatus;

/**
 * 全局错误码。
 *
 * <p><b>编号规则</b>：按模块分段，一眼能看出错误属于哪一块。
 * <pre>
 *   0        成功
 *   1xxxx    通用 / 框架级
 *   2xxxx    用户与认证
 *   3xxxx    动作库
 *   4xxxx    计划
 *   5xxxx    训练会话
 *   6xxxx    身体数据
 *   7xxxx    饮食
 *   8xxxx    管理后台
 *   9xxxx    外部服务（AI）
 * </pre>
 *
 * <p><b>为什么不直接用 HTTP 状态码当业务码</b>：
 * HTTP 状态码只有几十个且语义固定（404 = 资源不存在）。
 * 而业务错误有几十上百种——「邮箱已注册」「计划已过期」「组数超上限」
 * 如果都塞进 400，前端就没法区分该给用户看哪句提示。
 *
 * <p><b>但 HTTP 状态码仍然要设对</b>：不能一律返回 200。
 * 否则网关、监控告警、浏览器缓存、CDN 全都失效——
 * 从运维角度看，一个「HTTP 200 但 body 里写着系统错误」的响应是灾难。
 * 所以每个错误码同时携带 {@link HttpStatus}。
 */
public enum ErrorCode {

    // ==================== 成功 ====================
    SUCCESS(0, "成功", HttpStatus.OK),

    // ==================== 1xxxx 通用 / 框架级 ====================
    BAD_REQUEST(10000, "请求参数有误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(10001, "未登录或登录已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(10002, "没有权限执行此操作", HttpStatus.FORBIDDEN),
    NOT_FOUND(10003, "请求的资源不存在", HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(10004, "请求方法不支持", HttpStatus.METHOD_NOT_ALLOWED),
    SYSTEM_ERROR(10005, "系统繁忙，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR),
    // 请求体格式错误（JSON 语法本身就不合法，或类型对不上）
    MALFORMED_REQUEST(10006, "请求格式有误", HttpStatus.BAD_REQUEST),

    // ==================== 2xxxx 用户与认证 ====================
    EMAIL_ALREADY_EXISTS(20001, "该邮箱已被注册", HttpStatus.CONFLICT),
    USER_NOT_FOUND(20002, "用户不存在", HttpStatus.NOT_FOUND),
    PASSWORD_INCORRECT(20003, "邮箱或密码不正确", HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(20004, "账号已被禁用，请联系管理员", HttpStatus.FORBIDDEN),
    TOKEN_INVALID(20005, "登录凭证无效", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(20006, "登录已过期，请重新登录", HttpStatus.UNAUTHORIZED),
    REFRESH_TOKEN_INVALID(20007, "刷新凭证无效，请重新登录", HttpStatus.UNAUTHORIZED),
    OLD_PASSWORD_INCORRECT(20008, "原密码不正确", HttpStatus.BAD_REQUEST),

    // ==================== 3xxxx 动作库 ====================
    EXERCISE_NOT_FOUND(30001, "动作不存在", HttpStatus.NOT_FOUND),
    EXERCISE_IN_USE(30002, "该动作正被计划引用，无法删除", HttpStatus.CONFLICT),
    EXERCISE_NAME_DUPLICATED(30003, "同名动作已存在", HttpStatus.CONFLICT),

    // ==================== 4xxxx 计划 ====================
    PROGRAM_NOT_FOUND(40001, "计划不存在", HttpStatus.NOT_FOUND),
    /**
     * 计划处于不可编辑状态（已归档 / 已完成）。
     *
     * <p><b>原名 {@code PROGRAM_ALREADY_STARTED}（「计划已开始」），已更名。</b>
     * 原语义来自「已开始的计划不能改，要建新版本」的版本化设计——
     * 该设计已降级（见 REQUIREMENTS 6.3 不变量 2），
     * 「已开始」现在**恰恰是可以改的**，而且改了不影响历史（靠会话快照）。
     *
     * <p>数字码 40002 保持不变，客户端不受影响。
     * 但原来的文案「计划已开始，不能直接修改」会**主动误导用户**
     * （让他以为开始的计划改不了），所以文案必须跟着改。
     */
    PROGRAM_NOT_EDITABLE(40002, "计划已归档或已完成，不能修改", HttpStatus.CONFLICT),

    /**
     * 乐观锁冲突：提交时带的版本号与服务端不一致。
     *
     * <p>含义是「你读到计划之后，有别人改过它」。
     * 客户端应重新拉取详情再让用户重新提交，**不要自动重试**——
     * 自动重试等于用旧数据覆盖别人的改动，正是这个检查要防的事。
     */
    PROGRAM_VERSION_CONFLICT(40003, "计划已被其他设备修改，请刷新后重试", HttpStatus.CONFLICT),
    SUPERSET_GROUP_INVALID(40004, "超级组配置不合法", HttpStatus.BAD_REQUEST),
    PROGRAM_DAY_NOT_FOUND(40005, "训练日不存在", HttpStatus.NOT_FOUND),
    /**
     * 计划里使用了当前版本算不出来的目标强度。
     *
     * <p>V1 只支持绝对重量。{@code %1RM} 需要先有 1RM 数据，
     * {@code RPE} 本身就是「不指定重量」——两者都算不出具体公斤数。
     *
     * <p><b>单独给一个错误码而不是复用 BAD_REQUEST</b>：
     * 前端要据此**引导用户去设置 1RM**，而不是笼统提示「参数有误」。
     */
    PROGRAM_TARGET_UNSUPPORTED(40006, "计划使用了当前版本暂不支持的目标强度", HttpStatus.BAD_REQUEST),

    // ==================== 5xxxx 训练会话 ====================
    SESSION_NOT_FOUND(50001, "训练记录不存在", HttpStatus.NOT_FOUND),
    SESSION_ALREADY_COMPLETED(50002, "该训练已结束，无法修改", HttpStatus.CONFLICT),
    SESSION_VERSION_CONFLICT(50003, "该训练已在其他设备更新", HttpStatus.CONFLICT),
    SET_RECORD_INVALID(50004, "组记录数据不合法", HttpStatus.BAD_REQUEST),

    // ==================== 6xxxx 身体数据 ====================
    BODY_METRIC_NOT_FOUND(60001, "身体数据不存在", HttpStatus.NOT_FOUND),
    BODY_METRIC_VALUE_OUT_OF_RANGE(60002, "数值超出合理范围", HttpStatus.BAD_REQUEST),
    PHOTO_UPLOAD_FAILED(60003, "照片上传失败", HttpStatus.INTERNAL_SERVER_ERROR),
    PHOTO_TYPE_NOT_ALLOWED(60004, "不支持的照片格式", HttpStatus.BAD_REQUEST),

    // ==================== 7xxxx 饮食 ====================
    NUTRITION_LOG_NOT_FOUND(70001, "饮食记录不存在", HttpStatus.NOT_FOUND),
    NUTRITION_INSUFFICIENT_DATA(70002, "记录天数不足，无法估算消耗", HttpStatus.BAD_REQUEST),

    // ==================== 8xxxx 管理后台 ====================
    ADMIN_PERMISSION_DENIED(80001, "无管理员权限", HttpStatus.FORBIDDEN),
    CANNOT_DISABLE_SELF(80002, "不能禁用自己的账号", HttpStatus.BAD_REQUEST),

    // ==================== 9xxxx 外部服务 ====================
    AI_SERVICE_UNAVAILABLE(90001, "AI 服务暂时不可用", HttpStatus.SERVICE_UNAVAILABLE),
    AI_SERVICE_DISABLED(90002, "AI 功能已关闭", HttpStatus.BAD_REQUEST),
    AI_RESPONSE_INVALID(90003, "AI 返回内容无法解析", HttpStatus.BAD_GATEWAY),
    ;

    /** 业务错误码。前端依据它做分支判断，比依据 message 文本可靠得多 */
    private final int code;

    /** 给用户看的提示文案。注意：这是可以直接展示给终端用户的 */
    private final String message;

    /** 对应的 HTTP 状态码，由全局异常处理器设置到响应上 */
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    /**
     * 按业务码查找枚举。
     *
     * @return 找到则返回对应枚举；找不到返回 {@link #SYSTEM_ERROR}，避免返回 null 导致空指针
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode ec : values()) {
            if (ec.code == code) {
                return ec;
            }
        }
        return SYSTEM_ERROR;
    }
}
