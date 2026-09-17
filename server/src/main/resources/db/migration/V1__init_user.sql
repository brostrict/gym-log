-- ============================================================
-- V1 —— 用户表
--
-- 命名规则（Flyway 强制要求）：
--   V<版本号>__<描述>.sql
--   注意是**两个下划线**。写成一个下划线 Flyway 会报错。
--
-- 执行规则：
--   按版本号从小到大执行，每个脚本**只执行一次**。
--   执行记录存在 flyway_schema_history 表里。
--   ⚠️ 已经执行过的脚本**不要再改**——Flyway 会校验 checksum 并报错。
--      要改结构就新建 V2、V3……
-- ============================================================

CREATE TABLE `user`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- ---------- 账号 ----------
    `email`            VARCHAR(128) NOT NULL COMMENT '登录邮箱，唯一',
    `password_hash`    VARCHAR(100) NOT NULL COMMENT 'BCrypt 哈希。BCrypt 固定输出 60 字符，留余量到 100',
    `nickname`         VARCHAR(50)  NOT NULL COMMENT '昵称',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '账号状态：1=正常，0=禁用（管理员操作）',

    -- ---------- 个人资料（对应 REQUIREMENTS M1-5）----------
    `gender`           TINYINT      NULL COMMENT '性别：0=未设置，1=男，2=女',
    `birth_year`       SMALLINT     NULL COMMENT '出生年份。不存完整生日——算年龄只需要年份，少存少泄露',
    `height_cm`        DECIMAL(5, 1) NULL COMMENT '身高（厘米），如 175.5',
    `goal`             VARCHAR(32)  NULL COMMENT '训练目标：MUSCLE_GAIN / FAT_LOSS / STRENGTH / GENERAL',
    `experience`       VARCHAR(32)  NULL COMMENT '训练经验：BEGINNER / INTERMEDIATE / ADVANCED',
    `unit_pref`        VARCHAR(8)   NOT NULL DEFAULT 'kg' COMMENT '单位偏好：kg / lb。库里一律存 kg，只在显示层换算',

    -- ---------- 预留：第三方登录（V1 不实现，见 REQUIREMENTS 4.2）----------
    `provider`         VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '登录方式：local / wechat / apple',
    `provider_user_id` VARCHAR(128) NULL COMMENT '第三方平台的用户标识',

    -- ---------- 审计字段 ----------
    `email_verified`   TINYINT      NOT NULL DEFAULT 0 COMMENT '邮箱是否已验证。V1 不做验证流程，字段先留着',
    `last_login_at`    DATETIME     NULL COMMENT '最后登录时间',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删，1=已删（MyBatis-Plus 自动维护）',

    PRIMARY KEY (`id`),

    -- 邮箱唯一。⚠️ 注意：唯一索引**不区分逻辑删除**，
    -- 所以软删除一个用户后，他的邮箱仍然占着位置，无法被重新注册。
    -- V1 的处理：管理员删除用户时走**物理删除**（见 REQUIREMENTS M10-D-5 的级联删除）。
    UNIQUE KEY `uk_user_email` (`email`),

    -- 第三方登录查询用。V1 没数据，但建索引成本为零，以后加不用改表
    KEY `idx_user_provider` (`provider`, `provider_user_id`),

    -- 后台按注册时间排序 / 看板统计新增用户
    KEY `idx_user_created_at` (`created_at`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';

-- 说明几个刻意的选择：
--
-- 1. utf8mb4 而不是 utf8
--    MySQL 的 "utf8" 是**假的 UTF-8**，只支持 3 字节，存不了 emoji 和部分生僻字。
--    昵称里出现 emoji 会直接报错。utf8mb4 才是真正的 UTF-8。
--
-- 2. utf8mb4_0900_ai_ci 排序规则
--    MySQL 8 的默认排序规则。ai = accent insensitive（忽略重音），
--    ci = case insensitive（忽略大小写）。所以 'A@x.com' 和 'a@x.com' 会被判为重复邮箱，
--    这正是我们想要的——避免同一个邮箱注册出两个账号。
--
-- 3. 不用外键约束
--    这是国内互联网公司的普遍做法（阿里规范里明确写了）。
--    原因：外键在高并发下会因为锁竞争影响性能，且分库分表后无法维护。
--    完整性靠应用层保证 + 定期对账。
