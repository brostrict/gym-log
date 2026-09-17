-- ============================================================
-- V2 —— Refresh Token 表
--
-- ⚠️ 注意：V1__init_user.sql 已经执行过了，**不能再去改它**——
--    Flyway 会校验 checksum，改了会导致启动失败。
--    要改结构就新建 V2、V3…… 这就是版本化迁移的工作方式。
-- ============================================================

CREATE TABLE `refresh_token`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',

    `user_id`    BIGINT       NOT NULL COMMENT '所属用户',

    -- ---------- 为什么不存 token 原文 ----------
    -- refresh token 的有效期长达 30 天，一旦数据库泄露，
    -- 攻击者拿到原文就能直接冒充所有用户——比泄露密码还严重
    -- （密码至少还是哈希的）。
    --
    -- 存哈希后，攻击者拿到的是哈希值，无法反推出原文，也就无法使用。
    --
    -- 用 SHA-256 而不是 BCrypt 的原因见 RefreshTokenService 的类注释。
    `token_hash` CHAR(64)     NOT NULL COMMENT 'token 原文的 SHA-256 十六进制（64 字符）',

    `expires_at` DATETIME     NOT NULL COMMENT '过期时间',

    -- ---------- 撤销机制：这张表存在的全部理由 ----------
    -- 纯 JWT 是无状态的，签发后无法提前失效。
    -- 有了这两个字段，才能支持「登出」「改密码后踢掉所有设备」
    -- 「怀疑泄露时紧急撤销」。
    `revoked`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已撤销：0=有效，1=已撤销',
    `revoked_at` DATETIME     NULL COMMENT '撤销时间',

    -- ---------- 审计信息 ----------
    -- V1 不展示，但记录成本为零。将来做「登录设备管理」时直接可用。
    `created_ip` VARCHAR(45)  NULL COMMENT '签发时的 IP。45 字符是为了兼容 IPv6',
    `user_agent` VARCHAR(255) NULL COMMENT '签发时的 User-Agent，用于识别设备',

    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签发时间',

    PRIMARY KEY (`id`),

    -- 唯一索引：既能防止哈希碰撞，也让「按 token 查记录」走索引
    UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),

    -- 查询「某用户当前所有有效 token」：登出全部设备、或展示登录设备列表
    KEY `idx_refresh_token_user` (`user_id`, `revoked`),

    -- 清理过期记录用。定时任务会定期删除过期数据，避免表无限膨胀
    KEY `idx_refresh_token_expires` (`expires_at`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='刷新令牌';

-- 说明两个刻意的选择：
--
-- 1. 没有 `deleted` 逻辑删除字段
--    其他表都有（MyBatis-Plus 全局配置了 logic-delete-field: deleted），
--    但这张表**语义上不需要**——「撤销」就是它的删除，
--    由 `revoked` 字段表达，语义比 `deleted` 更准确。
--    MyBatis-Plus 对没有该字段的实体不会施加逻辑删除，两者不冲突。
--
-- 2. 不加外键约束
--    同 user 表的理由：外键在高并发下因锁竞争影响性能，
--    且分库分表后无法维护。完整性靠应用层保证。
--    代价是删除用户时需要**手动清理**其 token（见 M10-D-5 的级联删除要求）。
