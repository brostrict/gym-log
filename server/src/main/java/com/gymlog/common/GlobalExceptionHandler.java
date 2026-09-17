package com.gymlog.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>兜住所有从 Controller 漏出来的异常，统一转成 {@link Result} 格式。
 *
 * <p><b>{@code @RestControllerAdvice} 是什么</b>：
 * 它是 {@code @ControllerAdvice} + {@code @ResponseBody} 的组合。
 * Spring 会把所有 Controller 抛出的异常都交给这里处理，
 * 不需要在每个 Controller 里写 try-catch。
 *
 * <p><b>三条处理原则</b>：
 * <ol>
 *   <li><b>业务异常当「预期内事件」，系统异常当「bug」</b>——
 *       前者打 WARN 且不打栈（量大、无价值），后者打 ERROR 且打全栈（要排查）。</li>
 *   <li><b>绝不把异常栈返回给客户端</b>——
 *       栈里有类名、方法名、SQL 片段，等于给攻击者画地图。</li>
 *   <li><b>HTTP 状态码要设对</b>——
 *       不能一律 200，否则网关、监控、缓存全部失效。</li>
 * </ol>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================================================================
    // 一、业务异常 —— 预期内的失败，不是 bug
    // ==================================================================

    /**
     * 处理业务异常。
     *
     * <p>日志用 WARN 且**不打堆栈**：业务失败是设计内的分支
     * （比如「邮箱已注册」在用户手滑时天天发生），
     * 打全栈会把日志刷爆且毫无排查价值。
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBizException(BizException e, HttpServletRequest request) {
        log.warn("业务异常 | {} {} | code={} | message={}",
                request.getMethod(), request.getRequestURI(),
                e.getErrorCode().getCode(), e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(Result.fail(e.getErrorCode(), e.getMessage()));
    }

    // ==================================================================
    // 二、参数校验失败 —— 客户端传的数据不合法
    // ==================================================================

    /**
     * 处理 {@code @RequestBody} 上的 {@code @Valid} 校验失败。
     *
     * <p>例如注册接口的 {@code RegisterRequest} 上标了
     * {@code @Email}、{@code @Size(min=8)}，用户传了不合法的值就会走到这里。
     *
     * <p><b>关于返回哪条错误</b>：这里只返回<b>第一条</b>错误信息。
     * 原因是 mobile 端通常用 Toast 展示，把「邮箱格式不对; 密码太短; 昵称不能为空」
     * 一股脑塞给用户反而看不清。
     * 如果将来需要表单逐字段高亮，可以给 {@link Result} 加一个 {@code errors}
     * 字段返回 {@code Map<字段名, 错误信息>}——但那是需要时再做，不要提前设计。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();

        // 日志里记录全部错误，方便排查；但只把第一条返回给客户端
        log.warn("参数校验失败 | {} {} | {}",
                request.getMethod(), request.getRequestURI(),
                fieldErrors.stream()
                        .map(fe -> fe.getField() + "=" + fe.getRejectedValue() + "(" + fe.getDefaultMessage() + ")")
                        .collect(Collectors.joining(", ")));

        String message = fieldErrors.isEmpty()
                ? ErrorCode.BAD_REQUEST.getMessage()
                : fieldErrors.get(0).getDefaultMessage();

        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST, message));
    }

    /**
     * 处理查询参数 / 表单参数的校验失败（非 {@code @RequestBody}）。
     *
     * <p>和上面的区别：{@code @Valid} 标在 {@code @ModelAttribute} 或
     * 普通对象参数上时抛的是 {@code BindException}，而不是
     * {@code MethodArgumentNotValidException}。
     * 两者是 Spring 不同版本/不同绑定方式的产物，都要处理，否则会漏到兜底分支变成 500。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBindException(BindException e, HttpServletRequest request) {
        log.warn("参数绑定失败 | {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
        String message = fieldErrors.isEmpty()
                ? ErrorCode.BAD_REQUEST.getMessage()
                : fieldErrors.get(0).getDefaultMessage();

        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST, message));
    }

    /** 缺少必填的请求参数，例如 {@code ?page=} 没传 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        log.warn("缺少请求参数 | {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST, "缺少必填参数：" + e.getParameterName()));
    }

    /** 参数类型对不上，例如 {@code ?id=abc} 而接口期望的是数字 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        log.warn("参数类型错误 | {} {} | {}={}", request.getMethod(), request.getRequestURI(),
                e.getName(), e.getValue());

        return ResponseEntity
                .status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(Result.fail(ErrorCode.BAD_REQUEST, "参数 " + e.getName() + " 格式不正确"));
    }

    /**
     * 请求体无法解析——JSON 语法错误，或字段类型对不上。
     *
     * <p>注意：不要把 {@code e.getMessage()} 直接返回给客户端，
     * 它可能包含类名和字段路径（如 {@code com.gymlog.dto.RegisterRequest["age"]}），
     * 属于内部结构泄露。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        log.warn("请求体解析失败 | {} {} | {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        return ResponseEntity
                .status(ErrorCode.MALFORMED_REQUEST.getHttpStatus())
                .body(Result.fail(ErrorCode.MALFORMED_REQUEST));
    }

    // ==================================================================
    // 三、框架级异常 —— 请求本身就不合法
    // ==================================================================

    /** 请求方法不对，例如接口只接受 POST，客户端发了 GET */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        log.warn("请求方法不支持 | {} {} | 支持的方法是 {}",
                request.getMethod(), request.getRequestURI(), e.getSupportedHttpMethods());

        return ResponseEntity
                .status(ErrorCode.METHOD_NOT_ALLOWED.getHttpStatus())
                .body(Result.fail(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 访问了不存在的路径。
     *
     * <p>不加这个处理器的话，静态资源找不到会落到兜底分支变成 500，
     * 而客户端明明只是访问了一个不存在的地址，应该是 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(
            NoResourceFoundException e, HttpServletRequest request) {
        log.warn("资源不存在 | {} {}", request.getMethod(), request.getRequestURI());

        return ResponseEntity
                .status(ErrorCode.NOT_FOUND.getHttpStatus())
                .body(Result.fail(ErrorCode.NOT_FOUND));
    }

    // ==================================================================
    // 四、兜底 —— 没被上面任何一条接住的，都是 bug
    // ==================================================================

    /**
     * 兜底处理。
     *
     * <p><b>能走到这里的，都是没预料到的异常</b>——空指针、数组越界、
     * 数据库连接断开等等。这类必须：
     * <ul>
     *   <li>打 ERROR 级别 + <b>完整堆栈</b>（要靠它定位问题）</li>
     *   <li>只给客户端返回笼统的「系统繁忙」，<b>绝不带任何内部信息</b></li>
     * </ul>
     *
     * <p>另外：用户看到的提示里不要出现「异常」「错误码」这类词——
     * 普通用户看不懂，只会觉得这个 App 有问题。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception e, HttpServletRequest request) {
        log.error("未预期的异常 | {} {}", request.getMethod(), request.getRequestURI(), e);

        return ResponseEntity
                .status(ErrorCode.SYSTEM_ERROR.getHttpStatus())
                .body(Result.fail(ErrorCode.SYSTEM_ERROR));
    }
}
