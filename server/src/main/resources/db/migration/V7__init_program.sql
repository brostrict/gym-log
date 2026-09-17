-- ============================================================
-- V7 —— 训练计划
--
-- 这是全项目最复杂的一组表，五张表层层嵌套：
--
--   program              计划（含版本号）
--     └─ week_template   周结构（含强度修饰，如 deload −40%）
--          └─ day_template          训练日模板（推日 A / 拉日 A / 腿日 A）
--               └─ prescribed_exercise   处方动作（含超级组标记）
--                    └─ prescribed_set    逐组处方（5×5 递增就靠它）
--
-- ============================================================


-- ------------------------------------------------------------
-- 计划
-- ------------------------------------------------------------
CREATE TABLE `program`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,

    `user_id`      BIGINT       NOT NULL COMMENT '所属用户。计划是私有数据，不像动作库有内置的',

    `name`         VARCHAR(64)  NOT NULL COMMENT '计划名称，如「8周增肌计划」',

    -- ---------- 内置模板来源 ----------
    -- 从模板创建的计划记录来源，便于统计「哪个模板最受欢迎」，
    -- 也便于将来给用户「以模板最新版重新开始」的能力。
    -- NULL 表示完全自定义的计划。
    `template_code` VARCHAR(64) NULL COMMENT '来源模板编码，NULL=完全自定义',

    -- ---------- 周期 ----------
    --
    -- total_weeks = 0 表示**不限期计划**（持续进行，没有结束日期）。
    -- 这是 REQUIRMENTS M3-B-1 要求的两种模式之一——
    -- 很多人的训练计划是长期维持的，没有「第 8 周结束」这种概念。
    `total_weeks`  INT          NOT NULL DEFAULT 0 COMMENT '总周数，0=不限期',
    `start_date`   DATE         NULL COMMENT '开始日期',
    `end_date`     DATE         NULL COMMENT '结束日期。不限期计划为 NULL',

    -- ---------- 版本 ----------
    --
    -- ⚠️ 这是 REQUIREMENTS 6.3「不变量 2」的载体。
    --
    -- 用户修改一个**已经开始**的计划时，不直接改原记录，而是：
    --   ① 原计划标记为「已归档」
    --   ② 新建一条 program 记录，version + 1，指向同一逻辑计划
    --
    -- 训练会话在创建时记录它用的是哪个 program_id，
    -- 所以历史记录永远指向旧版本，不会被后续修改影响。
    `version`      INT          NOT NULL DEFAULT 1 COMMENT '版本号，修改已开始的计划时递增',

    -- 同一逻辑计划的多个版本共享这个 id。
    -- 第一个版本的 root_id = 自己的 id。
    -- 用于「查这个计划的所有版本」「查最新版本」。
    `root_id`      BIGINT       NULL COMMENT '逻辑计划 id，第一个版本等于自身 id',

    -- ---------- 状态 ----------
    -- ACTIVE   进行中
    -- PAUSED   暂停（不算作未完成，不计入完成率分母）
    -- ARCHIVED 已归档（被新版本取代）
    -- FINISHED 已完成
    `status`       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/PAUSED/ARCHIVED/FINISHED',

    `description`  VARCHAR(500) NULL COMMENT '计划说明',

    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted`      TINYINT      NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),
    KEY `idx_program_user_status` (`user_id`, `status`),
    KEY `idx_program_root` (`root_id`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='训练计划';


-- ------------------------------------------------------------
-- 周模板
--
-- 为什么需要这张表（而不是在 day_template 里直接标周次）：
--
--   8 周 × 每周 3 练 = 24 个训练日。
--   如果每周的结构都要单独建 day_template，就是 24 条记录，
--   而且改一个动作要改 8 遍。
--
--   拆开后：3 个训练日结构 + 8 个强度修饰 = 11 条记录。
--   处方值在**创建训练会话时**展开计算。
-- ------------------------------------------------------------
CREATE TABLE `week_template`
(
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,

    `program_id`      BIGINT        NOT NULL,

    -- 第几周，从 1 开始
    `week_number`     INT           NOT NULL COMMENT '周序号，从 1 开始',

    -- 这一周的计划训练次数。允许每周不同（如 deload 周可能少练一次）
    `sessions_per_week` INT         NOT NULL DEFAULT 3 COMMENT '本周计划训练次数',

    -- ---------- 强度修饰（周期化的核心）----------
    --
    -- 这三个字段描述「这一周相对基准强度怎么调」。
    -- 展开处方值时：最终重量 = 基准重量 × (1 + weight_adjust_pct/100)
    --
    -- 例：
    --   第 1-4 周   weight_adjust_pct = 0     基准强度
    --   第 5-7 周   weight_adjust_pct = 5     加 5%
    --   第 8 周     weight_adjust_pct = -40   deload
    `weight_adjust_pct`  DECIMAL(5, 2) NOT NULL DEFAULT 0 COMMENT '重量调整百分比，deload 用负数',

    -- 组数调整：+1 表示这周每个动作多做一组
    `set_adjust`         INT           NOT NULL DEFAULT 0 COMMENT '组数调整，可正可负',

    -- 是否 deload 周。**这个标记很重要**——
    -- 统计「计划完成率」时 deload 周要排除，
    -- 否则用户按计划减量反而被算成「没完成」。
    `is_deload`          TINYINT       NOT NULL DEFAULT 0 COMMENT '是否减量周',

    `note`            VARCHAR(255)  NULL COMMENT '本周说明，如「适应期」「冲强度」',

    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    -- 一个计划里周序号唯一
    UNIQUE KEY `uk_week_program_number` (`program_id`, `week_number`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='周模板';


-- ------------------------------------------------------------
-- 训练日模板
--
-- 定义「一个训练日长什么样」，**不含周次信息**。
-- 同一个「推日 A」在第 1 周和第 5 周是结构相同的，
-- 只是强度不同（由 week_template 的修饰决定）。
-- ------------------------------------------------------------
CREATE TABLE `day_template`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,

    `program_id`   BIGINT       NOT NULL,

    -- 计划内的第几个训练日，从 1 开始。
    -- 注意「训练日」不是「星期几」——用户可能周三练推日、
    -- 周六也练推日，或者某周只练两次。
    -- 第几个练是顺序，具体哪天练由用户的日程决定。
    `day_number`   INT          NOT NULL COMMENT '第几个训练日，从 1 开始',

    `name`         VARCHAR(64)  NOT NULL COMMENT '训练日名称，如「推日 A」',

    -- 是否休息日。休息日也占一个编号，
    -- 这样「第 3 天是休息」在计划里是明确的，而不是「没有第 3 天」。
    `is_rest_day`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否休息日',

    `note`         VARCHAR(255) NULL,

    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_day_program_number` (`program_id`, `day_number`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='训练日模板';


-- ------------------------------------------------------------
-- 处方动作
--
-- 训练日里的一个动作，以及「目标是什么」。
-- ------------------------------------------------------------
CREATE TABLE `prescribed_exercise`
(
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,

    `day_template_id`   BIGINT        NOT NULL,

    `exercise_id`       BIGINT        NOT NULL COMMENT '引用动作库',

    -- ---------- 排序 ----------
    -- 普通动作用 order_index 排序。
    -- 超级组内的动作，先按 superset_group 排，再按 order_in_group 排。
    `order_index`       INT           NOT NULL DEFAULT 0 COMMENT '在训练日内的顺序',

    -- ---------- 超级组 ----------
    --
    -- 超级组**不是独立实体**，只是动作上的一个分组标记。
    --
    --   superset_group = NULL  → 普通动作，做完一组就休息
    --   superset_group = 1     → 属于第 1 个超级组
    --   order_in_group = 1,2,3 → 组内执行顺序
    --
    -- 执行语义（见 TIMER-SPEC 1.5）：
    --   组内动作之间**不休息**，一轮做完才休息
    `superset_group`    INT           NULL COMMENT '超级组编号，NULL=普通动作',
    `order_in_group`    INT           NULL COMMENT '组内顺序，从 1 开始',

    -- ---------- 默认目标（可被 prescribed_set 覆盖）----------
    --
    -- 为什么同时有「动作级默认值」和「逐组处方」两层：
    --   大多数动作的每一组目标相同（3组×10次），
    --   逐组建记录是冗余的。
    --   只有需要递增/递减的动作才建 prescribed_set 记录。
    --
    -- 展开时的规则：如果该组有 prescribed_set 记录就用它，
    -- 否则回落到动作级的这些默认值。
    `target_sets`       INT           NOT NULL DEFAULT 3 COMMENT '目标组数',
    `target_reps_min`   INT           NULL COMMENT '目标次数下限',
    `target_reps_max`   INT           NULL COMMENT '目标次数上限。等于 min 时表示固定次数',
    `rest_sec`          INT           NOT NULL DEFAULT 90 COMMENT '组间休息秒数',

    -- ---------- 目标重量的三种写法 ----------
    -- 只能有一种生效，由 target_weight_type 决定：
    --   ABSOLUTE  绝对值  → target_weight 有值
    --   PERCENT_1RM 百分比 → target_weight_pct 有值
    --   RPE       主观强度 → target_rpe 有值
    `target_weight_type` VARCHAR(16)  NOT NULL DEFAULT 'ABSOLUTE' COMMENT 'ABSOLUTE/PERCENT_1RM/RPE',
    `target_weight`      DECIMAL(6, 2) NULL COMMENT '目标重量（kg）',
    `target_weight_pct`  DECIMAL(5, 2) NULL COMMENT '目标 %1RM',
    `target_rpe`         DECIMAL(3, 1) NULL COMMENT '目标 RPE',

    `note`              VARCHAR(255)  NULL COMMENT '备注，如「最后一组力竭」',

    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    KEY `idx_pe_day` (`day_template_id`, `order_index`),
    KEY `idx_pe_exercise` (`exercise_id`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='处方动作';


-- ------------------------------------------------------------
-- 逐组处方
--
-- ⚠️ 这张表是「计划灵活度」的关键，不能省。
--
-- 只支持「每个动作一个目标」会挡掉大量真实计划：
--
--   5×5 递增：  第1组 60kg，第2组 65kg，第3组 70kg，第4-5组 70kg
--   5/3/1：     75% / 85% / 95% 三组不同
--   金字塔：     12次 / 10次 / 8次 / 6次，重量递减
--   递减组：     最后一组做完立即减重继续
--
-- 实现成本不高（就是多一张表），但不实现就没法支持这些计划。
--
-- 注意：**只为需要特殊目标的组建记录**。
-- 3组×10次这种统一目标不需要建三条记录，用动作级默认值即可。
-- 展开时：有记录用记录，没记录回落默认值。
-- ------------------------------------------------------------
CREATE TABLE `prescribed_set`
(
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT,

    `prescribed_exercise_id` BIGINT       NOT NULL,

    `set_number`            INT           NOT NULL COMMENT '第几组，从 1 开始',

    -- 组类型：热身组不计入容量统计
    `set_type`              VARCHAR(16)   NOT NULL DEFAULT 'WORKING' COMMENT 'WARMUP/WORKING/FAILURE/DROP',

    `target_reps`           INT           NULL COMMENT '本组目标次数。NULL=用动作级默认',
    `target_reps_min`       INT           NULL COMMENT '次数区间下限',
    `target_reps_max`       INT           NULL COMMENT '次数区间上限',

    `target_weight`         DECIMAL(6, 2) NULL COMMENT '本组目标重量',
    `target_weight_pct`     DECIMAL(5, 2) NULL COMMENT '本组目标 %1RM',
    `target_rpe`            DECIMAL(3, 1) NULL COMMENT '本组目标 RPE',

    `rest_sec`              INT           NULL COMMENT '本组后的休息秒数。NULL=用动作级默认',

    `note`                  VARCHAR(255)  NULL,

    `created_at`            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_pset_exercise_number` (`prescribed_exercise_id`, `set_number`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='逐组处方';


-- ============================================================
-- 关键不变量（写在数据库注释里不够，代码里也要保证）
-- ============================================================
--
-- 【不变量 A】训练会话创建时，把处方**深拷贝**为快照。
--
--   会话不存 prescribed_exercise_id 外键，而是复制一份处方值。
--   否则用户第 5 周改了训练日的动作，前 4 周的历史会显示成改后的样子。
--   见 REQUIREMENTS 6.3。
--
-- 【不变量 B】计划的任何改动产生新版本号。
--
--   见 program.version 的注释。
--
-- 【不变量 C】超级组只改变执行顺序，不改变计量。
--
--   A1 做 3 组、A2 做 3 组，就是各记 3 组，与是否组成超级组无关。
--   绝不能把超级组存成一条合并记录——那会让容量计算、PR 判定、
--   单动作历史全部失效。
