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
 * <p><b>附带的安全好处</b>：{@code toString()} 的格式是
 * {@code LoginResponse[accessToken=..., user=...]}，只包含声明的字段。
 * 如果用 Lombok 的 {@code @Data} 生成 toString，将来给类加字段时
 * 很容易不小心把敏感字段也带进去。
 *
 * <p><b>⚠️ 这个对象会被 JSON 序列化返回给客户端，所以包含 token。
 * 任何时候都不要把它打进日志。</b>
 */
public record LoginResponse(

        /** 访问令牌。客户端后续请求要在 Header 里带 {@code Authorization: Bearer <token>} */
        String accessToken,

        /**
         * 令牌类型，固定 {@code "Bearer"}。
         *
         * <p>这是 OAuth 2.0 规范的约定。客户端拼接 Header 时不用硬编码
         * {@code "Bearer "} 前缀，而是读这个字段——将来换成别的认证方案时客户端不用改。
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
     * 直接返回 {@code User} 实体虽然方便，但等于把整张表暴露给客户端，
     * 每次给表加字段都要重新审视「这个字段能不能给前端看」。
     */
    public record UserBrief(
            Long id,
            String email,
            String nickname
    ) {
    }

    /** 便捷构造：自动填 {@code tokenType} 为 Bearer */
    public static LoginResponse of(String accessToken, long expiresIn, UserBrief user) {
        return new LoginResponse(accessToken, "Bearer", expiresIn, user);
    }
}
