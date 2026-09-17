package com.gymlog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymlog.common.ErrorCode;
import com.gymlog.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未认证时的响应处理器 —— 返回 <b>401</b>。
 *
 * <p><b>为什么需要它</b>：Spring Security 默认的 401 响应是
 * <b>一个空的响应体</b>（或者跳转到登录页，取决于配置）。客户端拿到
 * 只有状态码、没有内容的响应，不知道该给用户提示什么。
 *
 * <p>配置它之后，未认证的请求会得到和其他接口一致的 JSON：
 * <pre>
 *   HTTP 401
 *   {"code":10001,"message":"未登录或登录已过期"}
 * </pre>
 * 客户端就能用同一套逻辑解析所有响应。
 *
 * <p><b>401 和 403 的分工</b>：
 * <ul>
 *   <li><b>401（本类）</b>：不知道你是谁 —— 没带 token、token 无效或过期。
 *       客户端应该**跳转登录页**。</li>
 *   <li><b>403（{@link RestAccessDeniedHandler}）</b>：知道你是谁，但你没权限。
 *       客户端应该**提示「无权限」**，跳登录页没有意义——重新登录也还是没权限。</li>
 * </ul>
 *
 * <p><b>命名由来</b>：{@code EntryPoint} 是「入口」的意思——
 * 它定义了「未认证用户试图访问受保护资源时，从哪个入口被挡回去」。
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // ⚠️ 注意：这里**不要**把 authException.getMessage() 返回给客户端。
        // 它会包含内部细节，比如 "JWT expired at 2026-09-17T14:00:00Z"、
        // 类名、甚至密钥相关的提示。这些对排查问题的人是噪音，
        // 对攻击者却是有用的信息。
        //
        // 详细原因应该记在服务端日志里（步骤 1.7 的过滤器已经记了 debug 日志）。
        writeJson(response, ErrorCode.UNAUTHORIZED);
    }

    /**
     * 把 {@link Result} 序列化成 JSON 写入响应。
     *
     * <p><b>为什么三个设置都不能少</b>：
     * <ul>
     *   <li>{@code setStatus} —— 不设的话默认是 200，客户端会以为请求成功了</li>
     *   <li>{@code setContentType} —— 不设的话浏览器可能按 HTML 解析，中文乱码</li>
     *   <li>{@code setCharacterEncoding} —— 单独设 contentType 有时不够，
     *       显式声明 UTF-8 才能保证中文正确</li>
     * </ul>
     */
    private void writeJson(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(errorCode)));
    }
}
