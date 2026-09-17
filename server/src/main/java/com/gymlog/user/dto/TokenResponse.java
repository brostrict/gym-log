package com.gymlog.user.dto;

/**
 * 令牌对响应，用于刷新接口。
 *
 * <p><b>为什么不复用 {@link LoginResponse}</b>：刷新时客户端**已经有用户信息了**
 * （它本来就是登录过的），再返回一遍是冗余。接口只返回它真正需要的东西。
 *
 * <p>这也是接口设计的一个原则：**返回值和这个接口的语义要匹配**。
 * 「刷新令牌」这个动作的产出就是新令牌，不是用户资料。
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {

    public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
