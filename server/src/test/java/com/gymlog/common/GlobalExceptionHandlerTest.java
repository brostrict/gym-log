package com.gymlog.common;

import com.gymlog.exercise.dto.ExerciseQuery;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 异常响应的文案。
 *
 * <p>重点守两件事：**不泄露内部结构**、**错误信息对开发者有用**。
 * 这两条很容易在改异常处理时被破坏，而破坏之后不会有任何测试失败——
 * 只会让客户端某天收到一串 Java 类名。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final HttpServletRequest request = mock(HttpServletRequest.class);

    @Test
    @DisplayName("枚举值非法时：不泄露类名，且列出所有合法值")
    void enumMismatchIsTranslated() {
        BindException ex = bindFailure("movementPattern", "HORIZONTALPUSH");

        ResponseEntity<Result<Void>> response = handler.handleBindException(ex, request);

        String message = response.getBody().getMessage();

        // ① 不泄露内部结构
        assertThat(message)
                .as("错误信息里不能出现包名或类名")
                .doesNotContain("com.gymlog")
                .doesNotContain("java.lang.String")
                .doesNotContain("Failed to convert");

        // ② 把客户端传的值回显出来（他自己传的，回显是安全的）
        assertThat(message).contains("HORIZONTALPUSH");

        // ③ 列出合法值——这是开发者最需要的信息，不用去翻 Swagger
        assertThat(message)
                .contains("HORIZONTAL_PUSH")
                .contains("VERTICAL_PULL")
                .contains("SQUAT");

        // ④ 中文
        assertThat(message).contains("值不正确");
    }

    @Test
    @DisplayName("非枚举字段的类型错误只回显值和字段名")
    void nonEnumMismatchHasNoValueList() {
        BindException ex = bindFailure("size", "abc");

        String message = handler.handleBindException(ex, request).getBody().getMessage();

        assertThat(message)
                .contains("size")
                .contains("abc")
                .doesNotContain("com.gymlog")
                // size 是 Integer，不该冒出一个「可选值」列表
                .doesNotContain("可选值");
    }

    @Test
    @DisplayName("普通校验失败仍用注解上写的 message")
    void plainValidationKeepsCustomMessage() {
        BindException ex = new BindException(new ExerciseQuery(), "exerciseQuery");
        ex.rejectValue("size", "Max", "每页最多 100 条");

        String message = handler.handleBindException(ex, request).getBody().getMessage();

        // 非 typeMismatch 的错误必须原样透传——
        // 走了 describe() 之后仍然要保证这一点，
        // 否则 @NotBlank(message="邮箱不能为空") 这类自定义文案就全丢了
        assertThat(message).isEqualTo("每页最多 100 条");
    }

    @Test
    @DisplayName("回显的值过长时截断，避免撑爆响应和日志")
    void truncatesLongRejectedValue() {
        String huge = "x".repeat(5000);
        BindException ex = bindFailure("size", huge);

        String message = handler.handleBindException(ex, request).getBody().getMessage();

        assertThat(message.length()).isLessThan(200);
        assertThat(message).contains("…");
    }

    /**
     * 造一个和 Spring 真实行为一致的绑定失败。
     *
     * <p><b>⚠️ 不能用 {@code rejectValue(field, code, message)}</b>——
     * 那个三参重载的第三个参数是**错误消息**，不是被拒绝的值。
     * 用它的话 {@code getRejectedValue()} 会去读目标对象上该字段的当前值
     * （比如 {@code size} 会得到 20），断言就全错了。
     *
     * <p>直接构造 {@link org.springframework.validation.FieldError} 才能
     * 同时指定「被拒绝的值」和「错误码」——这两个正是生产代码分支和回显时用的。
     */
    private BindException bindFailure(String field, Object rejectedValue) {
        BindException ex = new BindException(new ExerciseQuery(), "exerciseQuery");
        ex.addError(new FieldError(
                "exerciseQuery",
                field,
                rejectedValue,
                false,                              // bindingFailure
                new String[]{"typeMismatch"},       // codes，getCode() 取第一个
                null,
                // defaultMessage 模拟 Spring 塞进去的那段原始英文异常文本
                "Failed to convert property value of type 'java.lang.String' to required type "
                        + "'com.gymlog.exercise.MovementPattern' for property '" + field + "'"));

        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/exercises");
        return ex;
    }
}
