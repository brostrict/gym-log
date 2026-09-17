package com.gymlog.user.dto;

/**
 * 登录成功响应。
 *
 * <p><b>用 record 而不是普通类</b>：这只是一个「数据载体」，
 * 构造后不该被修改，也不需要有行为。record 一行就能表达清楚：
 * <ul>
 *   <li>字段自动 {@code private final}</li>
 *   <li>自动生成全参构造器、getter、{@code equals}、{@code hashCode}、{@code toString}</li>
 *   <li>语义明确——看到 record 就知道「这是个不可变的数据载体」</li>
 * </ul>
 *
 * <p><b>⚠️ 这个对象会被 JSON 序列化返回给客户端，包含两个 token。
 * 任何时候都不要把它打进日志。</b>
 */
public record LoginResponse(

        /**
         * 访问令牌。有效期短（1 小时），每个请求都要带：
         * {@code Authorization: Bearer <token>}
         */
        String accessToken,

        /**
         * 刷新令牌。有效期长（30 天），**只在换取新 access token 时使用**。
         *
         * <p><b>客户端应该把它存在安全的地方</b>：
         * 移动端用 Keychain / Keystore（如 flutter_secure_storage），
         * **不要存在普通的 SharedPreferences 或 localStorage 里**——
         * 那些地方其他应用或 XSS 都能读到。
         *
         * <p>也**不要**随每个请求发送，只在 {@code /auth/refresh} 时用。
         */
        String refreshToken,

        /**
         * 令牌类型，固定 {@code "Bearer"}。
         * 客户端拼接 Header 时读这个字段，不要硬编码。
         */
        String tokenType,

        /** access token 剩余有效秒数。客户端据此决定何时该去刷新 */
        long expiresIn,

        /** 用户基本信息，客户端登录后可直接展示，省一次查询 */
        UserBrief user

) {

    /**
     * 用户简要信息。
     *
     * <p>刻意只放三个字段——**不含密码哈希、状态、provider 等内部字段**。
     */
    public record UserBrief(
            Long id,
            String email,
            String nickname
    ) {
    }

    /** 便捷构造：自动填 {@code tokenType} 为 Bearer */
    public static LoginResponse of(String accessToken, String refreshToken,
                                   long expiresIn, UserBrief user) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
