package com.gymlog.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求参数。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 个字符")
    private String email;

    /**
     * 明文密码。
     *
     * <p><b>注意这里没有 {@code @Size(min = 8)}</b>，而注册接口有。
     * 这不是疏漏——是刻意为之：
     * <ul>
     *   <li>如果将来把密码策略从 8 位改成 12 位，老用户的密码仍然是 8 位。
     *       登录时做长度校验会把老用户**挡在门外**。</li>
     *   <li>登录的职责是「验证凭据是否正确」，不是「检查密码是否符合当前策略」。
     *       策略检查属于注册和改密环节。</li>
     * </ul>
     * 这里只加 {@code @NotBlank} 和长度上限，上限是为了防止
     * 有人提交超长字符串耗尽服务端 CPU（BCrypt 的计算量虽然与长度无关，
     * 但字符串比较和网络传输是有关的）。
     */
    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度超出限制")
    private String password;
}
