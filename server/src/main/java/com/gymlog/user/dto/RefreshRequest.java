package com.gymlog.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新令牌请求。
 *
 * <p><b>为什么放在请求体而不是 Header</b>：
 * refresh token 只在刷新时用一次，且不像 access token 那样有标准的
 * {@code Authorization: Bearer} 约定。放请求体更直观。
 *
 * <p>更重要的原因：**如果放 Header，容易被中间层日志记录下来**。
 * 请求体在大多数访问日志里不会被打印。
 */
@Data
public class RefreshRequest {

    @NotBlank(message = "刷新令牌不能为空")
    private String refreshToken;
}
