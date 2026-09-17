package com.gymlog.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数。
 *
 * <p><b>为什么要单独建一个 DTO，不直接用 {@code User} 实体接收请求？</b>
 * <ul>
 *   <li><b>安全</b>：用实体接收意味着客户端可以传 {@code status}、{@code deleted}、
 *       {@code provider} 这些字段。攻击者传一个 {@code "status": 1} 就可能绕过禁用，
 *       传 {@code "emailVerified": 1} 就跳过了邮箱验证。
 *       用 DTO 只暴露「允许客户端提供的字段」，其余一律由服务端决定。</li>
 *   <li><b>校验规则不同</b>：注册时密码必填且是明文，入库时是哈希——
 *       同一个字段在两个阶段的含义和约束完全不同。</li>
 *   <li><b>解耦</b>：数据库表加字段时，接口契约不该跟着变。</li>
 * </ul>
 *
 * <p><b>三个校验注解的区别</b>（很容易用错）：
 * <pre>
 *   @NotNull   仅要求 != null            "  " 能通过
 *   @NotEmpty  要求 != null 且长度 > 0    "  " 能通过
 *   @NotBlank  要求 != null 且去掉首尾空格后长度 > 0   ← 处理用户输入通常用这个
 * </pre>
 */
@Data
public class RegisterRequest {

    /**
     * 邮箱，同时作为登录账号。
     *
     * <p>{@code @Email} 只做基本格式校验（有 @、有域名），
     * **不代表邮箱真实存在**。要确认存在性得发验证邮件——V1 不做（见 REQUIREMENTS M1）。
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    /**
     * 明文密码。
     *
     * <p><b>这个字段只在请求里存在，绝不会入库、不会进日志、不会被序列化返回。</b>
     * 入库前会被 BCrypt 加密成 {@code $2a$10$...} 形式的哈希。
     *
     * <p>长度下限设为 8：更短的话，即使有 BCrypt，离线暴力破解的成本也会明显降低。
     * 上限设为 32 是**必要的防护**——BCrypt 的计算量与密码长度无关，
     * 但如果不限长度，攻击者可以提交超长字符串（如 10MB）来消耗服务端 CPU 和内存。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度需在 8-32 位之间")
    private String password;

    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过 50 个字符")
    private String nickname;
}
