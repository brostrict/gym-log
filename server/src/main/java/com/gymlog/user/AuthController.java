package com.gymlog.user;

import com.gymlog.common.Result;
import com.gymlog.user.dto.LoginRequest;
import com.gymlog.user.dto.LoginResponse;
import com.gymlog.user.dto.RefreshRequest;
import com.gymlog.user.dto.RegisterRequest;
import com.gymlog.user.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、刷新、登出。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    /** Nginx / 网关转发时会把真实 IP 放在这个头里 */
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String HEADER_USER_AGENT = "User-Agent";

    private final UserService userService;

    /**
     * 注册新用户。
     *
     * <p>只返回用户 ID，不返回 token——注册和登录是两个独立职责。
     * 客户端注册成功后调一次 {@code /auth/login} 即可。
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(userService.register(request));
    }

    /**
     * 登录，返回 access token + refresh token。
     *
     * <p><b>为什么用 POST 而不是 GET</b>：密码要放在请求体里。
     * 如果用 GET，密码会出现在 URL 中，而 URL 会被记录在
     * 浏览器历史、服务器访问日志、反向代理日志、Referer 头里。
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(userService.login(
                request,
                resolveClientIp(httpRequest),
                httpRequest.getHeader(HEADER_USER_AGENT)
        ));
    }

    /**
     * 用 refresh token 换取新的令牌对。
     *
     * <p>调用时机：access token 过期（客户端收到 401 且 code=10001/20006）时。
     *
     * <p><b>客户端要注意并发问题</b>：如果多个请求同时收到 401，
     * 它们可能同时发起刷新——而轮换机制下**只有一个能成功**
     * （先到的作废了旧 token，后到的就失效了）。
     * 客户端应该保证「同一时刻只有一个刷新请求」，其他请求排队等它的结果。
     */
    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.ok(userService.refresh(
                request.getRefreshToken(),
                resolveClientIp(httpRequest),
                httpRequest.getHeader(HEADER_USER_AGENT)
        ));
    }

    /**
     * 登出，撤销当前设备的 refresh token。
     *
     * <p><b>这个接口必须公开</b>（不需要 access token）：
     * 否则 access token 过期的用户就无法登出了——
     * 而对用户来说，「登出」这个动作在任何时候都应该能做。
     *
     * <p>安全性由请求体里的 refresh token 本身保证——
     * 没有有效的 refresh token，调用这个接口什么也做不了。
     */
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody RefreshRequest request) {
        userService.logout(request.getRefreshToken());
        return Result.ok();
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    /**
     * 解析客户端真实 IP。
     *
     * <p><b>为什么不能直接用 {@code request.getRemoteAddr()}</b>：
     * 服务部署在 Nginx 或网关后面时，`getRemoteAddr()` 拿到的是
     * **代理服务器**的 IP（可能是 127.0.0.1），不是真实客户端 IP。
     *
     * <p>代理会把真实 IP 放在 {@code X-Forwarded-For} 头里，格式为：
     * <pre>
     *   X-Forwarded-For: 客户端IP, 代理1IP, 代理2IP
     * </pre>
     * **第一个才是真实客户端 IP**，后面的是各级代理。
     *
     * <p><b>⚠️ 安全提醒</b>：这个头是**客户端可以伪造的**。
     * 如果服务直接暴露在公网（没有前置代理），任何人都能随便填这个头。
     * 所以：
     * <ul>
     *   <li>它只能用于**记录和审计**，绝不能用于权限判断或限流</li>
     *   <li>生产环境应该在 Nginx 层用 {@code proxy_set_header} 覆盖它，
     *       而不是信任客户端传来的值</li>
     * </ul>
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(HEADER_X_FORWARDED_FOR);
        if (StringUtils.hasText(forwarded)) {
            // 取第一段，并去掉可能的首尾空格
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }

        String realIp = request.getHeader(HEADER_X_REAL_IP);
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
