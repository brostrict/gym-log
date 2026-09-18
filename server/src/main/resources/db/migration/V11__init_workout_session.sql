-- ============================================================
-- V11 —— 训练会话与快照
--
--   workout_session         一次训练
--     └─ session_exercise     快照：这次训练练了哪些动作
--          └─ session_set_target  快照：每一组的目标
--
-- 【这三张表的核心是「快照」】
--
-- 创建会话时，把计划里展开出来的处方值**深拷贝**一份进来。
-- 之后用户怎么改计划，这次训练的数值都不受影响。
-- 详见 REQUIREMENTS 6.3 不变量 1 与 2。
--
-- 【⚠️ 快照里绝不能出现 day_template_id / prescribed_exercise_id】
--
-- 计划结构编辑走的是**全量替换**（删掉所有子记录再重建，见步骤 2.14），
-- 所以那两张表的 id 每次编辑都会变。
-- 快照如果引用它们，用户改一次计划，所有历史会话就指向了不存在的行——
-- 而且**不会有任何报错**，只是历史显示成空白。
--
-- 所以这里存的是**值**：day_number + day_name + 动作名 + 处方数值。
--
-- 【⚠️ 顺带说明：V7 里 program.version 的注释已经过期】
--
-- 它还在描述「修改已开始的计划时新建一条记录」的版本化设计，
-- 那个设计已经降级了（REQUIREMENTS 6.3 不变量 2）——
-- 现在 version 是用作结构编辑的乐观锁计数器。
--
-- 但**已应用的迁移不能改**：Flyway 会校验 checksum，
-- 改一个字符启动就会失败。所以只能在这里说明，不能回头改 V7。
-- ============================================================


-- ------------------------------------------------------------
-- 一、训练会话
-- ------------------------------------------------------------
CREATE TABLE `workout_session`
(
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,

    `user_id`            BIGINT        NOT NULL COMMENT '所属用户',

    -- ---------- 幂等键（AC-5-1）----------
    --
    -- 离线优先的必然结果：手机上练完，网络不好，重试上传 3 次。
    -- 没有幂等键就会产生 3 条一模一样的训练记录。
    --
    -- 客户端在**创建会话时**生成一个 UUID 并一直带着它重试，
    -- 服务端靠唯一索引去重（重复上传时返回已存在的那条，而不是报错）。
    --
    -- NULL 表示不参与去重——手动补录的场景不需要幂等，
    -- 而 MySQL 的唯一索引允许多个 NULL（NULL ≠ NULL）。
    `client_key`         VARCHAR(64)   NULL COMMENT '客户端幂等键（UUID），NULL=不参与去重',

    -- ---------- 来源计划（可为空）----------
    --
    -- NULL = 临时训练（M4-A-3）：不按计划，自由记录。
    --
    -- ⚠️ program_id 是**唯一**允许保留的外键。
    -- program 表走逻辑删除，行不会真的消失，所以这个引用是安全的。
    -- 但它也只用于「这次训练属于哪个计划」的展示与统计，
    -- **所有显示用的数值都取自快照，不回查计划**。
    `program_id`         BIGINT        NULL COMMENT '来源计划，NULL=临时训练',

    -- ---------- 快照：这是哪一天 ----------
    --
    -- 注意存的是**值和名字**，不是 day_template_id（见文件头说明）。
    `day_number`         INT           NULL COMMENT '计划内的训练日序号',
    `day_name`           VARCHAR(64)   NULL COMMENT '快照：训练日名称，如「推日」',

    -- ---------- 快照：周期化位置 ----------
    --
    -- 记录这次训练落在第几周、当周强度怎么调。
    -- 用于训练总结里的「第 5 周 · 加重 5%」这类说明，
    -- 也用于将来按周聚合统计。
    `week_number`        INT           NULL COMMENT '快照：第几周',
    `is_deload`          TINYINT       NOT NULL DEFAULT 0 COMMENT '快照：是否减量周',
    `weight_adjust_pct`  DECIMAL(5, 2) NULL COMMENT '快照：当周重量调整百分比',

    -- ---------- 状态 ----------
    -- IN_PROGRESS  进行中（含断点续训的场景）
    -- COMPLETED    已完成
    -- ABANDONED    中途放弃
    --
    -- ⚠️ 只有 COMPLETED 才推进训练日轮转。
    -- 练到一半被叫走，下次应该还是练同一个训练日。
    `status`             VARCHAR(16)   NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS/COMPLETED/ABANDONED',

    -- ---------- 时间 ----------
    `started_at`         DATETIME      NOT NULL COMMENT '开始时间',
    `finished_at`        DATETIME      NULL COMMENT '结束时间',

    -- 训练时长由客户端记录后上报，**不在这里算 (finished_at - started_at)**。
    -- 因为用户可能中途暂停（接电话、等器械），
    -- 墙上时间不等于实际训练时间，而暂停时长只有客户端知道。
    `duration_sec`       INT           NULL COMMENT '实际训练时长（秒），由客户端上报',

    `note`               TEXT          NULL COMMENT '训练感受 / 备注',

    `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`            TINYINT       NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),

    -- 幂等：同一个用户的同一个 client_key 只能有一条
    UNIQUE KEY `uk_session_client_key` (`user_id`, `client_key`),

    -- 「我的训练历史」按时间倒序翻页
    KEY `idx_session_user_time` (`user_id`, `started_at`),
    KEY `idx_session_program` (`program_id`),

    -- 统计某个计划的完成场次（训练日轮转要用）
    KEY `idx_session_user_program_status` (`user_id`, `program_id`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='训练会话';


-- ------------------------------------------------------------
-- 二、会话动作快照
-- ------------------------------------------------------------
CREATE TABLE `session_exercise`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,

    `session_id`      BIGINT      NOT NULL,

    -- ---------- 动作身份：id 和名字都要存 ----------
    --
    -- 这两列看着冗余，其实各有用途，缺一不可：
    --
    --   exercise_id   用于**聚合**（「单动作历史 + sparkline」要按动作分组）。
    --                 但它是**尽力而为**的——用户可以删掉自己的自定义动作，
    --                 删了之后这个 id 就查不到东西了。
    --
    --   exercise_name **显示用的真相**。动作被删了、被改名了，
    --                 历史记录里显示的仍然是当时那个名字。
    --
    -- 只存 id：动作一删，历史就变成「未知动作」。
    -- 只存 name：没法按动作聚合，sparkline 做不出来。
    `exercise_id`     BIGINT      NULL COMMENT '动作 id，用于聚合；动作被删后可能查不到',
    `exercise_name`   VARCHAR(64) NOT NULL COMMENT '快照：动作名称，显示用，永不改变',

    `primary_muscle`  VARCHAR(20) NULL COMMENT '快照：主要肌群，用于肌群容量/组数统计',
    `metric_type`     VARCHAR(20) NULL COMMENT '快照：计量类型，决定跟练界面的录入控件',

    -- ---------- 执行顺序 ----------
    `order_index`     INT         NOT NULL COMMENT '在训练日内的顺序',

    -- 超级组分组。**不变量 4**：分组在快照中固化——
    -- 用户在会话进行中临时把两个动作组成超级组，只影响本次会话，不回写计划。
    `superset_group`  INT         NULL COMMENT '超级组编号，NULL=单独动作',
    `order_in_group`  INT         NULL COMMENT '超级组内顺序',

    -- ---------- 计划组数 ----------
    --
    -- ⚠️ 这个字段与 session_set_target 的行数是冗余的，是**刻意的反规范化**。
    -- 跟练界面要显示「第 2 组 / 共 5 组」，这是每屏都要用的分母；
    -- 为它单独查一次子表不划算。
    --
    -- 快照是不可变的，所以这份冗余不存在「两处不一致」的风险。
    `target_sets`     INT         NOT NULL DEFAULT 0 COMMENT '计划组数（冗余自 session_set_target）',

    `note`            VARCHAR(255) NULL COMMENT '快照：动作备注',

    -- ---------- 完成状态 ----------
    -- PENDING      未开始
    -- IN_PROGRESS  进行中
    -- COMPLETED    已完成
    -- SKIPPED      临场跳过（M4-D：用户换动作或跳过）
    --
    -- 跳过也要留记录，否则训练总结里会「少一个动作」，
    -- 用户想不起来当时是跳过了还是没安排。
    `status`          VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/IN_PROGRESS/COMPLETED/SKIPPED',

    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    KEY `idx_se_session` (`session_id`, `order_index`),

    -- 「单动作历史」要按动作反查所有会话
    KEY `idx_se_exercise` (`exercise_id`),

    -- 肌群维度的统计（周容量堆叠柱状图）
    KEY `idx_se_muscle` (`primary_muscle`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='会话动作快照';


-- ------------------------------------------------------------
-- 三、每组目标快照
-- ------------------------------------------------------------
CREATE TABLE `session_set_target`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,

    `session_exercise_id` BIGINT       NOT NULL,

    `set_number`          INT          NOT NULL COMMENT '第几组，从 1 开始',

    `set_type`            VARCHAR(16)  NOT NULL DEFAULT 'WORKING' COMMENT 'WARMUP/WORKING/FAILURE/DROP',

    -- ---------- 目标次数 ----------
    -- 固定次数填 target_reps；区间次数填 min/max。
    -- 展开算法已经把「min == max」归一化成固定次数形式，
    -- 所以这里不会再出现 min == max 的区间。
    `target_reps`         INT          NULL COMMENT '固定目标次数',
    `target_reps_min`     INT          NULL COMMENT '目标次数下限（区间）',
    `target_reps_max`     INT          NULL COMMENT '目标次数上限（区间）',

    -- ---------- 目标强度 ----------
    -- type + 三个可空值，与 prescribed_exercise 的结构一致。
    --
    -- ⚠️ 用四元组而不是一个裸重量，因为 RPE 处方算不出具体重量。
    -- 详见 ExpandedWorkout 的类注释。
    --
    -- V1 的展开只产出 ABSOLUTE（其余类型会明确报错），
    -- 但快照表保留完整结构——将来支持 %1RM 时不用改表。
    `target_weight_type`  VARCHAR(20)  NOT NULL DEFAULT 'ABSOLUTE' COMMENT 'ABSOLUTE/PERCENT_1RM/RPE',
    `target_weight`       DECIMAL(6, 2) NULL COMMENT '目标重量 kg（ABSOLUTE）',
    `target_weight_pct`   DECIMAL(5, 2) NULL COMMENT '目标 %1RM（PERCENT_1RM）',
    `target_rpe`          DECIMAL(3, 1) NULL COMMENT '目标 RPE',

    -- 展开时已经解析过优先级链（逐组 > 动作级 > 默认 90），
    -- 所以这里**不会是 NULL**——跟练倒计时必须有个确定的数字。
    `rest_sec`            INT          NOT NULL DEFAULT 90 COMMENT '本组之后的休息秒数（已解析）',

    `note`                VARCHAR(255) NULL,

    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    -- 一组只能有一条目标；同时保证「按组号取目标」是走索引的
    UNIQUE KEY `uk_sst_exercise_set` (`session_exercise_id`, `set_number`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='每组目标快照';
