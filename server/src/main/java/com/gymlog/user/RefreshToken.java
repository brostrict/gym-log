package com.gymlog.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 刷新令牌，对应 {@code refresh_token} 表。
 *
 * <p><b>为什么不存 token 原文</b>：refresh token 有效期长达 30 天，
 * 一旦数据库泄露，攻击者拿到原文就能冒充所有用户——**比泄露密码还严重**
 * （密码至少还是哈希的）。
 *
 * <p>存哈希后，攻击者拿到哈希无法反推原文，也就无法使用。
 *
 * <p><b>同样加了 {@code @JsonIgnore} 和 {@code @ToString.Exclude}</b>：
 * 理由和 {@link User#getPasswordHash()} 一样——
 * 这类「凭据性质的字段」绝不应该出现在 API 响应或日志里。
 */
@Data
@TableName("refresh_token")
public class RefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** token 原文的 SHA-256 十六进制值 */
    @JsonIgnore
    @ToString.Exclude
    private String tokenHash;

    private LocalDateTime expiresAt;

    /** 是否已撤销：0=有效，1=已撤销。见 {@link #REVOKED} */
    private Integer revoked;

    public static final int ACTIVE = 0;
    public static final int REVOKED = 1;

    private LocalDateTime revokedAt;

    private String createdIp;

    private String userAgent;

    private LocalDateTime createdAt;

    /** 是否仍然可用（未撤销且未过期） */
    public boolean isUsable() {
        return revoked != null && revoked == ACTIVE
                && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }
}
