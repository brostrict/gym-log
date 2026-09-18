-- ============================================================
-- V12 —— 组记录（用户实际做的那一组）
--
--   set_record   实际记录：这一组推了多少、做了几次、歇了多久
--
-- 【与 session_set_target 的关系】
--
--   session_set_target   计划：这一组**应该**推多重
--   set_record           实际：这一组**实际**推了多重
--
-- 两者按 (session_exercise_id, set_number) 对齐，但**不是一对一**：
--   · 用户可以临时加组（M4-D-3）→ set_record 比 target 多
--   · 用户可以少做几组 → set_record 比 target 少
--   · 用户可以不按目标重量做 → 数值完全不同，这是常态
--
-- 所以它们必须是两张表。合并的话，「计划」和「实际」就分不开了，
-- 而这两者的差值正是「符合率」这个指标的全部意义。
--
-- ============================================================
-- 【本迁移同时补两个 V11 漏掉的快照字段】
--
-- 写 3.3 时才发现：容量口径依赖两个会变的量，而它们**不在快照里**。
--
-- ① session_exercise.bw_factor
--    自重动作的容量 = 体重 × bw_factor × 次数（AC-7-8）。
--    而 M10-B-3 明确说管理员**可以编辑** bw_factor。
--    不快照的话，管理员把引体的系数从 1.00 改成 0.95，
--    用户三个月前的历史容量会**追溯性地变小**——
--    违反不变量 1，而且不会报任何错。
--
-- ② workout_session.body_weight_kg
--    REQUIREMENTS 3.3 明写「会话创建时**快照当时的体重**，保证历史容量可重算」。
--    体重每周都在变，不快照的话自重动作的历史容量永远算不准。
--
--     ⚠️ **这个字段现在恒为 NULL**：身体数据模块要到 Phase 4 才做，
--     现在没有体重可读。Phase 4 补上「读最近一次体重写入会话」即可。
--     字段先留好，是因为**事后加列比重构数据便宜得多**。
-- ============================================================


-- ------------------------------------------------------------
-- 一、补 V11 漏掉的两个快照字段
-- ------------------------------------------------------------

ALTER TABLE `session_exercise`
    ADD COLUMN `bw_factor` DECIMAL(4, 2) NULL
        COMMENT '快照：自重系数。容量 = 体重 × bw_factor × 次数'
        AFTER `metric_type`;

ALTER TABLE `workout_session`
    ADD COLUMN `body_weight_kg` DECIMAL(5, 2) NULL
        COMMENT '快照：训练当天的体重。自重动作容量用。Phase 4 前恒为 NULL'
        AFTER `weight_adjust_pct`;


-- ------------------------------------------------------------
-- 二、组记录
-- ------------------------------------------------------------
CREATE TABLE `set_record`
(
    `id`                  BIGINT        NOT NULL AUTO_INCREMENT,

    `session_exercise_id` BIGINT        NOT NULL,

    -- ⚠️ 列名用 set_number 而不是 set_index，与 session_set_target 保持一致。
    -- 两张表靠这个列对齐，名字不一样的话，写 join 的人会怀疑它们不是一回事。
    `set_number`          INT           NOT NULL COMMENT '第几组，从 1 开始',

    -- 组类型可以**在训练中改变**：计划里是正式组，用户练到力竭标成力竭组。
    -- 所以这里独立存一份，不引用 session_set_target 的值。
    `set_type`            VARCHAR(16)   NOT NULL DEFAULT 'WORKING'
        COMMENT 'WARMUP/WORKING/FAILURE/DROP。热身组不计入容量',

    -- ---------- 计量字段（宽表 + 判别器，见 REQUIREMENTS 6.5）----------
    --
    -- 用宽字段而不是 EAV：动作的计量类型只有四种，字段数量少且稳定。
    -- 哪些列有值由动作的 metric_type 决定：
    --
    --   WEIGHT_REPS        weight + reps
    --   REPS_ONLY          reps
    --   DURATION           duration_sec
    --   DISTANCE_DURATION  duration_sec + distance_m
    --
    -- 这样查「某动作的总容量」就是一句 SUM(weight * reps)，
    -- 不需要 join 一张属性表再透视——EAV 的问题就在这里：
    -- 灵活，但每次查询都要拼装。
    `weight`              DECIMAL(6, 2) NULL COMMENT '实际重量 kg（WEIGHT_REPS）',
    `reps`                INT           NULL COMMENT '实际次数（WEIGHT_REPS / REPS_ONLY）',
    `duration_sec`        INT           NULL COMMENT '实际时长秒（DURATION / DISTANCE_DURATION）',
    `distance_m`          DECIMAL(8, 2) NULL COMMENT '实际距离米（DISTANCE_DURATION）',

    -- RPE 可选（M4-C-5）。用户可以只记重量次数不记 RPE
    `rpe`                 DECIMAL(3, 1) NULL COMMENT '实际 RPE 1–10',

    -- ---------- 实际休息 ----------
    --
    -- M4-C-7：自动记录**实际**组间休息时长，与计划的 rest_sec 对比。
    -- 这个值只有客户端知道（服务端不知道用户什么时候放下手机又拿起来）。
    --
    -- NULL 表示没记录——最后一组之后没有休息，本来就没有值。
    `rest_actual_sec`     INT           NULL COMMENT '本组之后的实际休息秒数',

    `note`                VARCHAR(255)  NULL,

    -- 用户点「完成本组」的时刻。由客户端提供（离线训练时服务端不在场）
    `completed_at`        DATETIME      NOT NULL COMMENT '完成时刻，由客户端上报',

    `created_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    -- ⚠️ 这个唯一键同时承担两个职责：
    --
    -- ① **数据约束**：一个动作的第 3 组只能有一条记录
    -- ② **离线同步的幂等键**：客户端重放「记录第 3 组」时，
    --    服务端按 (session_exercise_id, set_number) 做 UPSERT，
    --    重复上传不会产生两条。AC-5-1 要的「重复上传 3 次不产生重复记录」
    --    在这里落地。
    --
    -- 用业务键而不是另加一个 client_key：组号本来就是这个动作内的唯一标识，
    -- 再引入一个 UUID 只是多一列要维护，还可能在重试时被重新生成。
    UNIQUE KEY `uk_set_record_exercise_number` (`session_exercise_id`, `set_number`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='组记录（实际完成）';
