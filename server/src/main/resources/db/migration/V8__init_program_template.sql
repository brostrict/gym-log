-- ============================================================
-- V8 —— 内置计划模板
--
-- 【设计决策】为什么结构存 JSON 而不是建一套镜像表
--
-- 备选方案：建 template_week / template_day / template_exercise /
--          template_set 五张表，与 program 那套一一对应。
--
-- 不选它的理由：
--   ① **完全重复**。两套结构相同的表，意味着两套实体、两套 Mapper、
--      两套增删改逻辑。改一个字段要改两处。
--   ② **模板是静态数据**。它在「从模板创建计划」时被复制一份，
--      之后模板本身不再被查询。没人会问「哪些模板包含杠铃卧推」
--      这种关系型问题。
--   ③ **形状天然对应**。JSON 的结构就是 ProgramCreateRequest 的结构，
--      从模板创建计划 = 读 JSON → 反序列化 → 调用 create()。
--      不需要写任何转换逻辑。
--
-- 代价：无法用 SQL 直接查模板内部（比如「模板里用了哪些动作」）。
-- 这类需求只在「删除动作时的引用检查」中出现，解析一下 JSON 即可，
-- 且本身是低频操作。
--
-- 判断依据：**当一个数据结构只被整体读写、从不被部分查询时，
-- 存 JSON 比建关系表更合适。**
-- ============================================================

CREATE TABLE `program_template`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,

    -- 稳定的英文编码，代码和接口里引用它。
    -- 与 role.code 同理：给人看的 name 可以改，code 是契约。
    `code`                VARCHAR(64)  NOT NULL COMMENT '模板编码，如 PPL_3DAY',

    `name`                VARCHAR(64)  NOT NULL COMMENT '模板名称，如「推拉腿三分化（每周3练）」',

    `description`         VARCHAR(500) NULL COMMENT '模板说明',

    -- ---------- 用于筛选和展示的元信息 ----------
    -- 这些字段从 structure 里「冗余」出来，目的是让列表页一次查完，
    -- 不用把每条模板的 JSON 都解析一遍。
    --
    -- 这是有意的反范式：**只读、低频变更的数据，冗余换查询简单是划算的**。
    `goal`                VARCHAR(32)  NOT NULL COMMENT '适用目标：MUSCLE_GAIN/STRENGTH/FAT_LOSS/GENERAL',
    `level`               VARCHAR(32)  NOT NULL COMMENT '适用水平：BEGINNER/INTERMEDIATE/ADVANCED',
    `sessions_per_week`   INT          NOT NULL COMMENT '每周训练次数',
    `total_weeks`         INT          NOT NULL DEFAULT 0 COMMENT '建议周期周数，0=不限期',
    `days_per_week`       INT          NOT NULL COMMENT '每周训练日数（含休息日）',
    `equipment_summary`   VARCHAR(255) NULL COMMENT '所需器械概览，如「杠铃、深蹲架、卧推凳」',
    `estimated_minutes`   INT          NULL COMMENT '单次预计时长（分钟）',

    -- ---------- 完整结构 ----------
    --
    -- 结构 = ProgramCreateRequest 的 JSON 形态：
    -- {
    --   "weeks": [ {weekNumber, sessionsPerWeek, weightAdjustPct, setAdjust, isDeload, note}, ... ],
    --   "days":  [ {dayNumber, name, isRestDay, note,
    --               exercises: [ {exerciseName, orderIndex, supersetGroup, orderInGroup,
    --                             targetSets, targetRepsMin, targetRepsMax, restSec,
    --                             targetWeightType, targetWeightPct, targetRpe, note,
    --                             sets: [...]}, ... ]}, ... ]
    -- }
    --
    -- ⚠️ 注意这里用 exerciseName 而不是 exerciseId。
    -- 原因：种子数据写死在迁移脚本里，而动作 id 是自增的，
    -- 不同环境（开发/测试/生产）的 id 不一样。
    -- 用名称在运行时查 id，才能保证各环境一致。
    --
    -- 代价：如果有人改了动作名称，模板就找不到动作了。
    -- 缓解：内置动作由管理端维护，改名时会有影响面提示（Phase 6）。
    `structure`           JSON         NOT NULL COMMENT '完整计划结构，用动作名称引用动作',

    -- 列表页排序权重
    `sort_order`          INT          NOT NULL DEFAULT 0,

    -- 上架 / 下架。下架的模板不再出现在选择列表，但已创建的计划不受影响
    `status`              TINYINT      NOT NULL DEFAULT 1 COMMENT '1=上架，0=下架',

    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`code`),
    KEY `idx_template_status_sort` (`status`, `sort_order`),
    KEY `idx_template_goal_level` (`goal`, `level`)

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='内置计划模板';
