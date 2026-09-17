package com.gymlog.security;

import com.gymlog.common.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器。
 *
 * <p><b>职责（只有一条）</b>：如果请求带了合法的 token，就把「当前用户是谁」
 * 放进 {@link SecurityContextHolder}，供后续代码读取。
 *
 * <p><b>它刻意不做的事</b>：判断「这个请求允不允许访问」。
 * 那是授权层（{@code SecurityFilterChain} 里的 {@code authorizeHttpRequests}）的职责。
 *
 * <p><b>为什么这个区分很重要</b>：
 * 常见的错误写法是「token 无效就直接返回 401」。这样会出问题——
 * <pre>
 *   用户的 token 过期了，但他访问的是一个【公开接口】（比如健康检查）
 *   → 过滤器抛出 401 → 公开接口也访问不了了
 * </pre>
 * 正确做法是：<b>token 无效就不认证</b>（什么都不做，继续往下走），
 * 让授权层去决定「未认证的请求能不能访问这个路径」。
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Authorization 请求头的标准前缀。注意末尾有个空格 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    /**
     * 每个请求都会走这里。
     *
     * <p><b>为什么继承 {@code OncePerRequestFilter} 而不是实现 {@code Filter}</b>：
     * 普通的 {@code Filter} 在「请求转发（forward）」或「include」时会被**重复执行**。
     * Spring Security 的过滤器链本身就涉及转发，
     * 用 {@code OncePerRequestFilter} 能保证一次请求只执行一次。
     * 虽然我们的过滤逻辑重复执行也无害（下面有 {@code == null} 判断），
     * 但重复解析 JWT 是白白的性能开销。
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        // 两个判断：
        //   ① token != null —— 请求带了 token
        //   ② getAuthentication() == null —— 当前还没有认证信息
        // 第 ② 条是防御性的：万一这个过滤器被执行了两次，
        // 或者上游已经设置了认证信息（比如测试代码），就不要覆盖。
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Long userId = jwtService.extractUserId(token);

            if (userId != null) {
                // ---------- 构造 Authentication 对象 ----------
                //
                // UsernamePasswordAuthenticationToken 有三个参数的构造器：
                //   principal   —— 「当前是谁」。这里放 userId
                //   credentials —— 「凭据」（密码）。token 认证场景下不需要，传 null
                //   authorities —— 「有什么权限」。RBAC 在 Phase 6 实现，这里先给空集合
                //
                // 注意用的是**三参数构造器**（带 authorities），
                // 它构造出来的是「已认证」状态。
                // 两参数构造器构造的是「未认证」状态，放进去等于没认证。
                var authentication = new UsernamePasswordAuthenticationToken(
                        userId,
                        null,
                        Collections.emptyList()
                );

                // 记录请求来源（IP、SessionId 等），审计日志会用到
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                // ---------- 放入 SecurityContext ----------
                //
                // SecurityContextHolder 底层是 **ThreadLocal**，
                // 所以放进去的认证信息**只在当前请求线程内可见**，
                // 请求结束会被 SecurityContextPersistenceFilter 清理掉。
                //
                // 这也是为什么「无状态」是安全的：
                // 下一个请求（哪怕是同一个用户）会是**另一个线程**，
                // 拿不到上一个请求的认证信息——必须重新带 token。
                SecurityContextHolder.getContext().setAuthentication(authentication);

                if (log.isDebugEnabled()) {
                    log.debug("JWT 认证成功 | userId={} | {} {}",
                            userId, request.getMethod(), request.getRequestURI());
                }
            }
            // 注意：userId == null（token 无效/过期）时**什么都不做**。
            // 不抛异常、不写响应——继续往下走，由授权层决定怎么处理。
            // 这样公开接口在 token 过期时依然可用。
        }

        // 无论有没有认证成功，都要放行到下一个过滤器。
        // 忘了这行的话，请求会在这里「静默消失」，客户端收到空响应或超时
        // —— 是新手最常踩的坑之一。
        filterChain.doFilter(request, response);
    }

    /**
     * 从 {@code Authorization} 请求头中提取 token。
     *
     * <p>标准格式：{@code Authorization: Bearer eyJhbGciOi...}
     *
     * @return token 字符串；没有或格式不对时返回 {@code null}
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");

        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return StringUtils.hasText(token) ? token : null;
        }

        // 也支持通过查询参数传 token —— 主要为了图片/文件下载场景：
        // <img src="/api/v1/photos/xxx?token=...">
        // 因为 <img> 标签发不出自定义 Header。
        //
        // ⚠️ 但要注意：URL 会被记录在访问日志、浏览器历史、Referer 头里，
        // 所以只对「只读、低敏感」的资源开放这种方式。
        // 本项目 V1 暂不支持，等 Phase 4 做体态照片时再评估。
        return null;
    }
}
