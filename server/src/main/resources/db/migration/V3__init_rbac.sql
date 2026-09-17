-- ============================================================
-- V3 —— RBAC 权限模型
--
-- ⚠️ 本步骤**只建表 + 种子数据**，不做权限校验。
--    完整的 @PreAuthorize 方法级鉴权在 Phase 6（管理后台）。
--
-- 为什么现在就建表：Phase 1 注册用户时就要**绑定默认角色**，
-- 否则 Phase 6 上线时存量用户全都没有角色，还得写数据修补脚本。
-- ============================================================

-- ------------------------------------------------------------
-- 角色表
-- ------------------------------------------------------------
CREATE TABLE `role`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- 代码里用它判断（hasRole('ADMIN')），所以必须是稳定的英文常量
    `code`        VARCHAR(32)  NOT NULL COMMENT '角色编码，如 USER / ADMIN。代码中引用它，不可随意改',

    -- 给人看的名称。改了不影响代码，只影响界面显示
    `name`        VARCHAR(64)  NOT NULL COMMENT '角色名称，如「普通用户」',

    `description` VARCHAR(255) NULL COMMENT '角色说明，写清楚这个角色能干什么',

    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`code`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色';


-- ------------------------------------------------------------
-- 权限表
-- ------------------------------------------------------------
CREATE TABLE `permission`
(
    `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- 权限标识，格式「资源:操作」，如 exercise:create
    -- 这个命名约定让权限的语义自解释，也便于按资源批量授权
    `code`       VARCHAR(64) NOT NULL COMMENT '权限标识，格式 资源:操作，如 exercise:create',

    `name`       VARCHAR(64) NOT NULL COMMENT '权限名称，如「新增动作」',

    -- 拆成两列存储，而不是只存 code：
    -- 便于「查出某个资源下的所有权限」这类查询，也便于后台界面按资源分组展示
    `resource`   VARCHAR(32) NOT NULL COMMENT '资源，如 exercise / user / template',
    `action`     VARCHAR(32) NOT NULL COMMENT '操作，如 create / read / update / delete',

    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_code` (`code`),
    KEY `idx_permission_resource` (`resource`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='权限';


-- ------------------------------------------------------------
-- 用户-角色关联
-- ------------------------------------------------------------
CREATE TABLE `user_role`
(
    `id`         BIGINT   NOT NULL AUTO_INCREMENT,

    `user_id`    BIGINT   NOT NULL COMMENT '用户',
    `role_id`    BIGINT   NOT NULL COMMENT '角色',

    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '授予时间',

    PRIMARY KEY (`id`),

    -- 联合唯一：防止同一个角色重复授予给同一个人
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),

    -- 查「这个角色下有哪些用户」用
    KEY `idx_user_role_role` (`role_id`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户角色关联';


-- ------------------------------------------------------------
-- 角色-权限关联
-- ------------------------------------------------------------
CREATE TABLE `role_permission`
(
    `id`            BIGINT   NOT NULL AUTO_INCREMENT,

    `role_id`       BIGINT   NOT NULL,
    `permission_id` BIGINT   NOT NULL,

    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_permission_permission` (`permission_id`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色权限关联';


-- ============================================================
-- 种子数据
-- ============================================================

-- ---------- 角色 ----------
INSERT INTO `role` (`code`, `name`, `description`)
VALUES ('USER', '普通用户', '记录自己的训练与身体数据，只能访问自己的数据'),
       ('ADMIN', '管理员', '维护动作库、计划模板与用户，可查看审计日志与数据看板');

-- ---------- 权限 ----------
-- 粒度到「资源:操作」。注意区分 read（查看）与 delete（破坏性操作）——
-- 将来可能有人需要「能看但不能删」，权限拆细了才能这么配。
INSERT INTO `permission` (`code`, `name`, `resource`, `action`)
VALUES
    -- 动作库
    ('exercise:read',   '查看动作',     'exercise', 'read'),
    ('exercise:create', '新增动作',     'exercise', 'create'),
    ('exercise:update', '编辑动作',     'exercise', 'update'),
    ('exercise:delete', '停用动作',     'exercise', 'delete'),

    -- 计划模板
    ('template:read',   '查看计划模板', 'template', 'read'),
    ('template:create', '新增计划模板', 'template', 'create'),
    ('template:update', '编辑计划模板', 'template', 'update'),
    ('template:delete', '下架计划模板', 'template', 'delete'),

    -- 用户管理
    ('user:read',       '查看用户',     'user',     'read'),
    ('user:update',     '编辑用户',     'user',     'update'),
    ('user:disable',    '禁用/启用用户', 'user',    'disable'),
    ('user:delete',     '删除用户',     'user',     'delete'),

    -- 审计与看板
    ('audit:read',      '查看审计日志', 'audit',    'read'),
    ('dashboard:read',  '查看数据看板', 'dashboard', 'read');

-- ---------- 角色-权限关联 ----------
--
-- ADMIN 拥有全部权限。用 SELECT 而不是硬编码 id：
-- 权限 id 是自增的，硬编码会在将来增删权限时错位。
-- 这条语句的语义也更清楚——「ADMIN 拥有所有权限」。
INSERT INTO `role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `role` r
         CROSS JOIN `permission` p
WHERE r.code = 'ADMIN';

-- ⚠️ USER 角色**不授予任何 permission**。
--
-- 原因：普通用户的所有操作都是「操作自己的数据」——
-- 查自己的训练记录、改自己的计划。这类权限用「是否登录」+
-- 「数据归属校验（WHERE user_id = ?）」就够了，
-- 不需要在 permission 表里为每个用户都建一份授权。
--
-- 把「自己的数据」也做成权限项会导致：
--   ① 每个新用户注册都要插几十条授权记录
--   ② 权限表膨胀到用户数 × 权限数
--
-- permission 表只为**管理端**的跨用户操作服务。
