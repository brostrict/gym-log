package com.gymlog.config;

import com.gymlog.common.JwtService;
import com.gymlog.security.JwtAuthenticationFilter;
import com.gymlog.security.RestAccessDeniedHandler;
import com.gymlog.security.RestAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置。
 *
 * <p>定义三件事：<b>哪些路径公开</b>、<b>哪些需要认证</b>、<b>被拒绝时返回什么</b>。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * 公开路径 —— 不需要任何凭据就能访问。
     *
     * <p><b>加进这个列表前先问自己：未登录的人访问它，会造成什么后果？</b>
     * 这个列表越短越安全。每加一条都是一次有意识的决定。
     */
    private static final String[] PUBLIC_PATHS = {
            // ---------- 认证相关：必须公开，否则没人能登录 ----------
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",

            // 登出也必须公开：如果要求带 access token，
            // 那么 access token 过期的用户就**无法登出**了——
            // 而对用户来说，「登出」在任何时候都应该能做。
            // 安全性由请求体里的 refresh token 本身保证。
            "/api/v1/auth/logout",

            // ---------- 健康检查：负载均衡器要能探活 ----------
            // 如果这个也要求认证，服务会被误判为不可用而被摘掉
            "/api/v1/system/ping",

            // ---------- Spring Boot 的错误转发终点 ----------
            // ⚠️ 这条很容易漏！任何异常最终都会转发到 /error，
            // 如果它被拦截，客户端收到的会是 401 而不是真实的错误码，
            // 排查问题时会被严重误导。
            "/error",
    };

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ---------- 关闭 CSRF ----------
                //
                // CSRF 攻击依赖「浏览器会自动带上 Cookie」。
                // 本项目的凭据是 Authorization 头里的 token，浏览器不会自动附带，
                // 跨域请求也会被浏览器拦截，所以 CSRF 攻击不成立。
                //
                // ⚠️ 但如果将来改用 Cookie 存 token，**必须把这里改回来**。
                .csrf(AbstractHttpConfigurer::disable)

                // 关闭默认表单登录和 HTTP Basic——它们返回 HTML，不适合 REST API
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // ---------- 无状态会话 ----------
                // 服务端不保存任何会话状态，每个请求自带凭据。
                // 这是水平扩展的前提：不需要 session 共享。
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ---------- 授权规则（顺序很重要：从上到下匹配，先匹配到的生效）----------
                .authorizeHttpRequests(auth -> auth

                        // CORS 预检请求必须放行。
                        // 浏览器发预检时不带 Authorization 头（这是 CORS 规范规定的），
                        // 如果它也要求认证，跨域请求永远无法成功。
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // 公开路径
                        .requestMatchers(PUBLIC_PATHS).permitAll()

                        // 管理端需要 ADMIN 角色。
                        // ⚠️ 目前是「死配置」——还没有任何 /admin 接口，
                        // 而且 RBAC 的权限装配在 Phase 6。
                        // 放在这里是为了把分层结构先立起来。
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

                        // 其余全部需要认证。
                        // 这一条是**安全默认值**——新增接口时如果忘了配规则，
                        // 默认是「需要登录」而不是「公开」。宁可多拦，不可漏放。
                        .anyRequest().authenticated()
                )

                // ---------- 认证/授权失败的响应 ----------
                // 不配的话，401 响应体是空的，403 可能返回 HTML，客户端无法解析
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)   // 401 未认证
                        .accessDeniedHandler(restAccessDeniedHandler)             // 403 无权限
                )

                // ---------- 插入 JWT 过滤器 ----------
                // 必须在授权判断之前执行，否则授权时拿不到认证信息
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 过滤器。
     *
     * <p><b>刻意不用 {@code @Component}</b>：Spring Boot 会把容器里所有
     * {@code Filter} 类型的 Bean 自动注册到 Servlet 过滤器链，
     * 那样它会在 Security 链【外】再执行一次，每个请求解析两遍 JWT。
     */
    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }
}
