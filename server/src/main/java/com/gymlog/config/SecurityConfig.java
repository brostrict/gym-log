package com.gymlog.config;

import com.gymlog.common.JwtService;
import com.gymlog.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置。
 *
 * <p><b>⚠️ 本步骤（1.7）的配置是「过渡态」</b>：目前所有接口都是放行的
 * （{@code anyRequest().permitAll()}），目的是先让 JWT 过滤器跑起来、
 * 把认证信息填进上下文，同时不破坏步骤 1.5/1.6 已经能用的接口。
 *
 * <p>步骤 1.8 会把放行规则改成「公开接口放行、业务接口需认证」，
 * 并补上 401/403 的统一响应处理。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;

    /**
     * 安全过滤器链。
     *
     * <p><b>为什么要有这个 Bean</b>：引入 {@code spring-boot-starter-security} 后，
     * Spring Boot 会自动装配一套默认配置——**锁住所有接口**，
     * 并在启动日志里打印一个随机密码（形如
     * {@code Using generated security password: 8f4c2a1e-...}）。
     * 那套默认配置是给「快速体验」用的，任何真实项目都要自己定义
     * {@code SecurityFilterChain} 来覆盖它。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // ---------- 关闭 CSRF ----------
                //
                // CSRF（跨站请求伪造）攻击依赖「浏览器会自动带上 Cookie」这一行为。
                // 本项目的认证凭据是 Authorization 头里的 token，
                // **不是 Cookie**——浏览器不会自动附带它，
                // 攻击者的网站无法伪造这个头（跨域请求会被浏览器拦截）。
                //
                // ⚠️ 但如果将来改成用 Cookie 存 token，**必须把 CSRF 打开**，
                // 否则就真的暴露在 CSRF 攻击下了。
                .csrf(AbstractHttpConfigurer::disable)

                // ---------- 关闭默认表单登录和 HTTP Basic ----------
                // 这两个是给传统服务端渲染页面用的，
                // 纯 REST API 不需要——它们会返回 HTML 登录页而不是 JSON。
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // ---------- 会话策略：无状态 ----------
                //
                // STATELESS 意味着 Spring Security **不会创建也不会使用 HttpSession**。
                // 每个请求都必须自带凭据（token），服务端不保存任何会话状态。
                //
                // 这是水平扩展的前提：任何一台服务器都能处理任何请求，
                // 不需要 session 共享（否则就得引入 Redis 做 session 存储）。
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ---------- 授权规则（步骤 1.8 会改这里）----------
                // 当前：全部放行。过滤器仍然会跑，只是不做拦截。
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )

                // ---------- 插入 JWT 过滤器 ----------
                //
                // addFilterBefore 把我们的过滤器放在
                // UsernamePasswordAuthenticationFilter **之前**。
                //
                // 为什么是这个位置：Spring Security 的过滤器链有严格顺序，
                // 认证类过滤器要在授权判断（FilterSecurityInterceptor）之前执行，
                // 否则授权时拿不到认证信息，所有请求都会被判为「未认证」。
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 过滤器。
     *
     * <p><b>⚠️ 这里刻意不用 {@code @Component} 注解</b>，原因是
     * Spring Boot 有一个容易踩的陷阱：
     *
     * <p>Spring Boot 会把容器里**所有 {@code Filter} 类型的 Bean**
     * 自动注册到 Servlet 容器的过滤器链上。如果 {@code JwtAuthenticationFilter}
     * 标了 {@code @Component}，那么：
     * <pre>
     *   ① 被 Spring Boot 自动注册 → 在 Security 过滤器链【之外】执行一次
     *   ② 又被 addFilterBefore 加进 Security 链 → 在链【内】再执行一次
     *
     *   结果：每个请求解析两遍 JWT，白白的性能开销，
     *        而且调试时看到日志打两遍会非常困惑。
     * </pre>
     *
     * <p>常见解法有两种：
     * <ul>
     *   <li><b>本项目的做法</b>：不标 {@code @Component}，在这里手动 new。
     *       简单直观，且这个过滤器本来也只服务于 Security 链。</li>
     *   <li>标 {@code @Component}，额外注册一个
     *       {@code FilterRegistrationBean} 并 {@code setEnabled(false)}
     *       来关掉自动注册。适合过滤器还需要被其他地方注入的情况。</li>
     * </ul>
     */
    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }
}
