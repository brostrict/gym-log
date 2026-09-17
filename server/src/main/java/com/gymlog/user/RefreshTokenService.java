package com.gymlog.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 刷新令牌的生命周期管理：签发、校验、撤销。
 *
 * <p><b>为什么 refresh token 不用 JWT</b>（本步骤最核心的问题）：
 *
 * <p>JWT 的特点是**无状态**——服务端不存任何东西，验签即可。这既是优点也是致命缺点：
 * <pre>
 *   用户手机丢了，想远程登出
 *   → JWT 是无状态的，服务端根本没有「这个 token 还有效」的记录
 *   → 无法让它提前失效
 *   → 只能干等它自然过期（30 天）
 * </pre>
 * <b>refresh token 必须可主动撤销，所以它不能是纯 JWT，必须有服务端记录。</b>
 *
 * <p>access token 则相反——它有效期只有 1 小时，且每个请求都要带（暴露面大），
 * 用无状态 JWT 正合适：省一次数据库查询，且泄露后的窗口有限。
 *
 * <p><b>这就是双 token 设计的本质</b>：
 * <table border="1">
 *   <tr><th></th><th>access token</th><th>refresh token</th></tr>
 *   <tr><td>形式</td><td>JWT（自包含）</td><td><b>随机字符串（存库）</b></td></tr>
 *   <tr><td>有效期</td><td>1 小时</td><td>30 天</td></tr>
 *   <tr><td>传输频率</td><td>每个请求</td><td>只在刷新时</td></tr>
 *   <tr><td>能否撤销</td><td>不能（等过期）</td><td><b>能</b></td></tr>
 *   <tr><td>校验成本</td><td>验签，无 IO</td><td>查一次库</td></tr>
 * </table>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    /**
     * 令牌随机字节数。
     *
     * <p>32 字节 = 256 位熵。用 Base64URL 编码后是 43 个字符。
     *
     * <p>这个长度下，暴力猜中的概率是 1/2^256——即使每秒尝试十亿次，
     * 到宇宙热寂也猜不完。
     */
    private static final int TOKEN_BYTES = 32;

    /**
     * 专用的安全随机数生成器。
     *
     * <p><b>为什么不用 {@code Math.random()} 或 {@code new Random()}</b>：
     * 它们是**伪随机**，种子可预测。攻击者只需观察到几个输出，
     * 就能推算出后续所有 token——这在实际攻击中发生过多次。
     *
     * <p>{@code SecureRandom} 使用操作系统的熵源（Windows 上是
     * {@code BCryptGenRandom}），输出不可预测。
     *
     * <p>它是线程安全的，所以可以做成静态常量复用，
     * 不必每次 new（每次 new 都要重新初始化熵源，有开销）。
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * URL 安全的 Base64 编码器，不带填充。
     *
     * <p>用 URL 安全变体是因为 token 会出现在 JSON、URL 参数、
     * HTTP 头里——标准 Base64 的 {@code +} 和 {@code /} 在这几处都需要转义。
     */
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenMapper refreshTokenMapper;
    private final com.gymlog.common.JwtProperties jwtProperties;

    /**
     * 签发一个新的 refresh token。
     *
     * @return **token 原文**。注意：原文只在这一次返回，库里存的是哈希，
     *         之后再也无法还原——所以调用方必须立刻把它返回给客户端。
     */
    @Transactional
    public String issue(Long userId, String ip, String userAgent) {
        String rawToken = generateRawToken();

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiresAt(LocalDateTime.now().plus(jwtProperties.getRefreshTokenTtl()));
        entity.setRevoked(RefreshToken.ACTIVE);
        entity.setCreatedIp(ip);
        entity.setUserAgent(truncate(userAgent, 255));

        refreshTokenMapper.insert(entity);

        return rawToken;
    }

    /**
     * 按原文查找**仍然可用**的 token 记录。
     *
     * @return 可用则返回实体；token 不存在、已撤销或已过期均返回 {@code null}
     */
    public RefreshToken findUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }

        RefreshToken entity = refreshTokenMapper.selectOne(
                new LambdaQueryWrapper<RefreshToken>()
                        .eq(RefreshToken::getTokenHash, sha256Hex(rawToken))
        );

        // 注意这里没有区分「不存在」和「已撤销/过期」——
        // 对客户端来说都只应该得到「刷新凭证无效，请重新登录」，
        // 区分开反而会泄露「这个 token 曾经存在过」的信息。
        return (entity != null && entity.isUsable()) ? entity : null;
    }

    /**
     * 撤销单个 token。
     *
     * <p>用「更新标志位」而不是物理删除，有两个原因：
     * <ul>
     *   <li><b>审计</b>：能查到「这个 token 什么时候被撤销的」</li>
     *   <li><b>检测重放</b>：如果已撤销的 token 再次被使用，
     *       说明它可能被窃取了——这时应该撤销该用户的**所有** token
     *       （见下面的 {@link #revokeAllForUser}）</li>
     * </ul>
     */
    @Transactional
    public void revoke(RefreshToken token) {
        if (token == null || token.getId() == null) {
            return;
        }
        refreshTokenMapper.update(null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getId, token.getId())
                        .eq(RefreshToken::getRevoked, RefreshToken.ACTIVE)
                        .set(RefreshToken::getRevoked, RefreshToken.REVOKED)
                        .set(RefreshToken::getRevokedAt, LocalDateTime.now())
        );
    }

    /**
     * 撤销某用户的**全部** token —— 登出所有设备。
     *
     * <p>使用场景：
     * <ul>
     *   <li>用户修改密码后（旧密码泄露了，所有会话都该失效）</li>
     *   <li>用户主动「登出所有设备」</li>
     *   <li>管理员禁用账号时</li>
     *   <li><b>检测到 token 重放时</b>——旧 token 被再次使用，
     *       说明它落到了别人手里，此时必须把所有会话踢掉</li>
     * </ul>
     *
     * @return 被撤销的 token 数量
     */
    @Transactional
    public int revokeAllForUser(Long userId) {
        int affected = refreshTokenMapper.update(null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getUserId, userId)
                        .eq(RefreshToken::getRevoked, RefreshToken.ACTIVE)
                        .set(RefreshToken::getRevoked, RefreshToken.REVOKED)
                        .set(RefreshToken::getRevokedAt, LocalDateTime.now())
        );
        if (affected > 0) {
            log.info("已撤销用户全部刷新令牌 | userId={} | 数量={}", userId, affected);
        }
        return affected;
    }

    /**
     * 清理已过期或已撤销且超过保留期的记录。
     *
     * <p>由定时任务调用（V1 暂未实现调度，先留接口）。
     * 不清理的话这张表会随用户活跃度无限增长。
     */
    @Transactional
    public int deleteExpiredBefore(LocalDateTime cutoff) {
        return refreshTokenMapper.delete(
                new LambdaQueryWrapper<RefreshToken>()
                        .lt(RefreshToken::getExpiresAt, cutoff)
        );
    }

    // ==================================================================
    // 内部工具
    // ==================================================================

    /** 生成随机 token 原文 */
    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return BASE64_ENCODER.encodeToString(bytes);
    }

    /**
     * 计算 SHA-256 十六进制值。
     *
     * <p><b>⚠️ 为什么这里用 SHA-256 而不是 BCrypt？</b>
     * 这是个容易被问到的点——密码用了 BCrypt，token 为什么不用？
     *
     * <table border="1">
     *   <tr><th></th><th>密码</th><th>refresh token</th></tr>
     *   <tr><td>来源</td><td>用户自己选</td><td><b>SecureRandom 生成</b></td></tr>
     *   <tr><td>熵</td><td>低（"password123" 约 30 位）</td><td><b>256 位</b></td></tr>
     *   <tr><td>是否可猜</td><td>可以（字典攻击）</td><td><b>不可能</b></td></tr>
     *   <tr><td>需要的哈希</td><td><b>慢哈希</b>（BCrypt）</td><td>快哈希即可（SHA-256）</td></tr>
     * </table>
     *
     * <p>核心逻辑：BCrypt「故意慢」是为了对抗**暴力枚举**。
     * 但 256 位熵的空间根本枚举不了——慢哈希没有任何收益，
     * 只会让每次刷新白白多花 80ms。
     *
     * <p><b>哈希的目的在这里不是「防猜测」，而是「防数据库泄露后直接可用」</b>：
     * 攻击者拿到 SHA-256 值反推不出原文，所以无法使用。
     */
    private String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须支持的算法，走不到这里
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 截断字符串到指定长度，避免超长 User-Agent 导致插入失败 */
    private String truncate(String s, int maxLength) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLength ? s : s.substring(0, maxLength);
    }
}
