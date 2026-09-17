package com.gymlog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymlog.common.ErrorCode;
import com.gymlog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 已认证但无权限时的响应处理器 —— 返回 <b>403</b>。
 *
 * <p><b>和 401 的区别（很重要的一个区分）</b>：
 * <pre>
 *   401 —— 「你是谁？」        没带 token / token 无效
 *          客户端应该 → 跳转登录页
 *
 *   403 —— 「我知道你是谁，但你不够格」  普通用户访问管理端接口
 *          客户端应该 → 提示「无权限」
 *          跳登录页没意义，重新登录一百次也还是没权限
 * </pre>
 *
 * <p>把这两个混为一谈是常见错误。混了之后，用户会被反复弹回登录页，
 * 却始终不知道自己其实是权限不够。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        // 403 通常意味着「有人在尝试访问不该访问的资源」，
        // 值得记录一条带用户身份的日志——可能是配置错误，也可能是攻击探测。
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.warn("权限不足 | userId={} | {} {}",
                auth == null ? "anonymous" : auth.getPrincipal(),
                request.getMethod(), request.getRequestURI());

        response.setStatus(ErrorCode.FORBIDDEN.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ErrorCode.FORBIDDEN)));
    }
}
