-- ============================================================
-- V4 —— 动作库
--
-- 动作库是**整个系统的地基**：计划、训练会话、组记录、指标统计
-- 全都挂在动作上。这一版把结构定下来。
-- ============================================================

CREATE TABLE `exercise`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',

    -- ---------- 归属：内置还是用户自定义 ----------
    --
    -- 0        = 内置动作（系统预置，所有人可见，仅管理员可改）
    -- 非 0     = 用户自定义动作，值是该用户的 id（仅本人可见）
    --
    -- ⚠️ 为什么用 0 而不是 NULL：
    -- 唯一索引 uk_exercise_user_name 依赖这一列。而 SQL 标准规定
    -- **NULL 不等于 NULL**——唯一索引里多个 NULL 被视为互不相同。
    -- 如果内置动作用 NULL，就能插入两个都叫「卧推」的内置动作，
    -- 唯一约束形同虚设。
    --
    -- 用 0 作哨兵值：真实自增 id 从 1 开始，永不冲突。
    `user_id`           BIGINT       NOT NULL DEFAULT 0 COMMENT '0=内置动作；其他=创建者用户id',

    -- ---------- 基本信息 ----------
    `name`              VARCHAR(64)  NOT NULL COMMENT '动作名称，如「杠铃卧推」',

    -- 别名用于搜索：用户搜「卧推」应该能匹配到「杠铃卧推」。
    -- 多个别名用英文逗号分隔（如 "卧推,bench press,平板卧推"）。
    -- 用逗号分隔而不是 JSON 数组：这个字段只用于 LIKE 模糊匹配，
    -- 不需要结构化查询，简单格式便于人工维护。
    `alias`             VARCHAR(255) NULL COMMENT '别名，多个用英文逗号分隔，用于搜索',

    -- ---------- 分类维度（三个正交的分类方式）----------
    --
    -- 为什么要三个维度，而不是一个「分类」字段：
    --   ① 肌群——用户想知道「今天练胸有哪些动作」
    --   ② 器械——用户想知道「酒店只有哑铃能练什么」
    --   ③ 动作模式——用于判断替代动作（水平推都能互相替代）
    -- 三个问题对应三种筛选，一个字段表达不了。

    `primary_muscle`    VARCHAR(32)  NOT NULL COMMENT '主要肌群：CHEST/BACK/LEGS/SHOULDERS/ARMS/CORE',

    `secondary_muscles` VARCHAR(255) NULL COMMENT '次要肌群，逗号分隔。V1 不参与统计，仅展示',

    `equipment`         VARCHAR(32)  NOT NULL COMMENT '器械：BARBELL/DUMBBELL/MACHINE/CABLE/BODYWEIGHT/BAND/KETTLEBELL/OTHER',

    `movement_pattern`  VARCHAR(32)  NULL COMMENT '动作模式：HORIZONTAL_PUSH/HORIZONTAL_PULL/VERTICAL_PUSH/VERTICAL_PULL/SQUAT/HINGE/LUNGE/CORE',

    -- ---------- 计量方式（决定组记录哪些字段有意义）----------
    --
    -- WEIGHT_REPS       重量 × 次数     杠铃卧推
    -- REPS_ONLY         仅次数          引体向上、俯卧撑
    -- DURATION          仅时长          平板支撑
    -- DISTANCE_DURATION 距离 + 时长     跑步
    --
    -- ⚠️ 用 VARCHAR 而不是 MySQL 的 ENUM 类型：
    -- MySQL ENUM 增删值要 ALTER TABLE，且顺序敏感（底层存的是序号，
    -- 调整顺序会让存量数据含义错乱）。用 VARCHAR + Java 枚举校验，
    -- 灵活且不会出这种事故。
    `metric_type`       VARCHAR(32)  NOT NULL COMMENT '计量类型：WEIGHT_REPS/REPS_ONLY/DURATION/DISTANCE_DURATION',

    -- ---------- 自重动作的容量系数 ----------
    --
    -- 自重动作的重量是 0，直接算 Σ(重量×次数) 会得到 0，
    -- 导致徒手训练完全不计入容量。
    --
    -- bw_factor（bodyweight factor，体重系数）表示这个动作
    -- 实际负荷相当于体重的多少倍：
    --   引体向上 / 双杠臂屈伸  1.00   （撑起全部体重）
    --   倒立撑                0.90
    --   俯卧撑                0.65   （脚撑地分担了一部分）
    --   反向划船              0.55
    --   臀桥 / 平板支撑        0.50
    --
    -- 容量公式：体重 × bw_factor × 次数
    --
    -- 只有 metric_type = REPS_ONLY 且是自重负荷的动作才需要填。
    -- 负重动作（weight_reps）留 NULL。
    `bw_factor`         DECIMAL(4, 2) NULL COMMENT '自重动作的体重系数，仅 REPS_ONLY 类需要',

    -- ---------- 其他属性 ----------
    `is_unilateral`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否单侧动作（如单臂哑铃划船）。影响录入 UI 与容量统计',

    -- ---------- 教学内容 ----------
    `instructions`      TEXT         NULL COMMENT '动作要领',
    `common_mistakes`   TEXT         NULL COMMENT '常见错误',

    -- ---------- 状态 ----------
    --
    -- ⚠️ 这里有两个「删除」相关的字段，用途完全不同，不要混淆：
    --
    --   status  —— 业务上的「停用」。管理员下架一个动作时用。
    --              历史训练记录**仍然正常显示**，只是新计划里选不到它。
    --   deleted —— MyBatis-Plus 的逻辑删除。用于「真的不要了」的场景。
    --
    -- 停用动作**绝不能物理删除或逻辑删除**——历史训练记录通过外键
    -- 引用它，删了之后「我三个月前练的是什么」就查不出来了。
    `status`            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用，0=停用（下架）',

    -- 内置动作在列表中的排序权重，让常见动作排在前面
    `sort_order`        INT          NOT NULL DEFAULT 0 COMMENT '排序权重，越小越靠前',

    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',

    PRIMARY KEY (`id`),

    -- 同一归属下动作名唯一。
    -- 内置动作之间不能重名（user_id 都是 0）；
    -- 每个用户的自定义动作之间也不能重名，但可以和内置动作重名
    -- （用户想自己定义一套「卧推」的练法，允许）。
    UNIQUE KEY `uk_exercise_user_name` (`user_id`, `name`),

    -- 列表页最常用的筛选：按肌群查、且只要启用的
    KEY `idx_exercise_muscle` (`primary_muscle`, `status`),

    -- 「酒店只有哑铃能练什么」
    KEY `idx_exercise_equipment` (`equipment`),

    -- 找替代动作：同动作模式的动作互相替代
    KEY `idx_exercise_pattern` (`movement_pattern`),

    -- 「我的自定义动作」
    KEY `idx_exercise_user` (`user_id`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='动作库';
