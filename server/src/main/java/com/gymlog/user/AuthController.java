package com.gymlog.user;

import com.gymlog.common.Result;
import com.gymlog.user.dto.LoginRequest;
import com.gymlog.user.dto.LoginResponse;
import com.gymlog.user.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、刷新、登出。
 *
 * <p>目前有注册和登录，刷新/登出在步骤 1.9 补齐。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 注册新用户。
     *
     * <p><b>{@code @Valid} 的作用</b>：触发 {@link RegisterRequest} 上标注的
     * JSR-303 校验注解。校验失败会抛 {@code MethodArgumentNotValidException}，
     * 被 {@code GlobalExceptionHandler} 捕获并转成 400 响应。
     *
     * <p><b>注意 {@code @Valid} 的位置很重要</b>：必须标在参数上。
     * 如果只标在 DTO 类的字段上而不加 {@code @Valid}，注解形同虚设——
     * 这是新手最常犯的错误之一，接口看起来有校验，实际上什么都不校验。
     *
     * <p><b>返回什么</b>：只返回用户 ID，不返回 token——
     * 注册和登录是两个独立职责，注册接口不负责签发凭证。
     * 客户端注册成功后调一次 {@code /auth/login} 即可（多一次请求，
     * 但接口语义清晰、职责单一）。
     * 若将来产品要求「注册即登录」，再单独评估。
     *
     * <p><b>为什么不返回 {@code User} 实体</b>：实体里有 {@code passwordHash}
     * 这类不该外泄的字段。虽然 {@code User} 上已经加了 {@code @JsonIgnore} 兜底，
     * 但依赖「记得给每个敏感字段加注解」本身就是风险——
     * 直接返回一个明确的、只有必要字段的对象更安全。
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return Result.ok(userId);
    }

    /**
     * 登录，成功后返回 access token。
     *
     * <p><b>为什么用 POST 而不是 GET</b>：密码要放在请求体里。
     * 如果用 GET，密码会出现在 URL 中，而 URL 会被记录在：
     * 浏览器历史、服务器访问日志、反向代理日志、Referer 头。
     * 这些地方都是明文可读的，等于到处散播密码。
     *
     * <p><b>为什么不用 HTTP Basic 认证</b>：Basic 认证把
     * {@code base64(用户名:密码)} 放在 Header 里，且**每次请求都要带**。
     * 一旦有一次请求被截获（比如误用了 HTTP 而非 HTTPS），
     * 密码就直接泄露。而这里密码只在登录这一次传输，之后用的是 token。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(userService.login(request));
    }
}
