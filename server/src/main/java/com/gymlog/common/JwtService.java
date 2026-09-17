package com.gymlog.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * <p><b>JWT 是什么</b>：一个用点号分隔成三段的字符串。
 * <pre>
 *   eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiIxIiwiZW1haWwiOiJhQGIuY29tIn0 . dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
 *   └── Header ──────┘   └── Payload ─────────────────────┘   └── Signature ─────────────────────────────────┘
 *
 *   Header    : {"alg":"HS256"}              算法声明
 *   Payload   : {"sub":"1","email":"a@b.com"} 业务数据
 *   Signature : HMAC-SHA256(header + "." + payload, 密钥)
 * </pre>
 *
 * <p><b>⚠️ 最关键的一点：Payload 只是 Base64 编码，不是加密。</b>
 * <pre>
 *   echo -n 'eyJzdWIiOiIxIn0' | base64 -d
 *   → {"sub":"1"}
 * </pre>
 * 任何人拿到 token 都能解开看到内容。<b>所以绝对不能往里放密码、手机号、
 * 身份证号这类敏感信息。</b>它的安全性来自「签名不可伪造」，不是「内容不可读」。
 *
 * <p><b>签名防的是什么</b>：攻击者改了 payload 里的 {@code sub}（想冒充别人），
 * 但他没有密钥，算不出对应的签名。服务端验签时对不上，直接拒绝。
 */
@Slf4j
@Service
public class JwtService {

    /** HS256 要求密钥至少 256 位 = 32 字节 */
    private static final int MIN_KEY_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey key;

    /**
     * 构造时就把密钥准备好，并做启动期校验。
     *
     * <p><b>为什么在构造器里校验而不是用时再检查</b>：
     * 配置错误应该在**启动时立刻失败**，而不是等到第一个用户登录才炸。
     * 这是「快速失败」原则——问题暴露得越早，排查成本越低。
     */
    public JwtService(JwtProperties properties) {
        this.properties = properties;

        if (!StringUtils.hasText(properties.getSecret())) {
            throw new IllegalStateException(
                    "JWT 密钥未配置。请在 application-dev.yml 中设置 jwt.secret，" +
                    "或通过环境变量 JWT_SECRET 提供。生成方式：head -c 64 /dev/urandom | base64");
        }

        byte[] keyBytes;
        try {
            // 密钥以 Base64 存储，这里解码成原始字节。
            // 用 Base64 而不是直接存原始字符串，是为了避免密钥里出现
            // 不可见字符（换行、控制符）导致 YAML 解析出意外结果。
            keyBytes = Decoders.BASE64.decode(properties.getSecret());
        } catch (IllegalArgumentException e) {
            // 有些人生成密钥时忘了 Base64 编码，直接贴了个随机字符串。
            // 这里降级成按 UTF-8 字节处理，比直接报错友好。
            log.warn("jwt.secret 不是合法的 Base64，将按 UTF-8 字符串处理");
            keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(String.format(
                    "JWT 密钥太短：当前 %d 字节，HS256 要求至少 %d 字节。"
                            + "生成方式：head -c 64 /dev/urandom | base64",
                    keyBytes.length, MIN_KEY_BYTES));
        }

        this.key = Keys.hmacShaKeyFor(keyBytes);
        log.info("JwtService 初始化完成 | access token 有效期={} | refresh token 有效期={}",
                properties.getAccessTokenTtl(), properties.getRefreshTokenTtl());
    }

    // ==================================================================
    // 签发
    // ==================================================================

    /**
     * 签发 access token。
     *
     * @param userId 用户 ID，放进标准声明 {@code sub}（subject）
     * @param email  用户邮箱，放进自定义声明，便于排查问题时不用查库
     */
    public String generateAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        return Jwts.builder()
                // sub 是 JWT 标准声明，语义就是「这个 token 代表谁」
                .subject(String.valueOf(userId))
                // 自定义声明。注意：这里的内容是**明文可读**的，
                // 所以只放邮箱这种「泄露了也不致命」的信息
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.getAccessTokenTtl())))
                .signWith(key)
                .compact();
    }

    // ==================================================================
    // 解析
    // ==================================================================

    /**
     * 解析并验签。
     *
     * <p>这一步同时做了三件事：
     * <ol>
     *   <li>验证签名——token 有没有被篡改</li>
     *   <li>验证过期——{@code exp} 是否已过</li>
     *   <li>验证格式——是不是一个合法的 JWT</li>
     * </ol>
     *
     * @throws JwtException 签名不对、已过期、格式非法时抛出。
     *                      调用方（步骤 1.7 的 Filter）负责捕获并转成 401。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 token 中取出用户 ID。
     *
     * @return 用户 ID；token 非法时返回 {@code null}（不抛异常，方便调用方处理）
     */
    public Long extractUserId(String token) {
        try {
            return Long.valueOf(parse(token).getSubject());
        } catch (JwtException | NumberFormatException e) {
            log.debug("token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** access token 有效期（秒）。返回给客户端，让它知道何时该刷新 */
    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtl().toSeconds();
    }

    /** refresh token 有效期（秒） */
    public long getRefreshTokenTtlSeconds() {
        return properties.getRefreshTokenTtl().toSeconds();
    }
}
