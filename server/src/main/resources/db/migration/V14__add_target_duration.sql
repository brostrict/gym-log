-- ============================================================
-- V14 —— 时长类动作的「目标持续时长」与「播报间隔」
--
-- 【要解决的问题】
--
-- 平板支撑这类等长收缩动作（metric_type = DURATION）的目标是**秒数**，
-- 但处方表里根本没有存秒数的列。之前的做法是把秒数**塞进次数字段**：
--
--   V9__seed_program_template.sql:204
--   {"exerciseName":"平板支撑", "targetRepsMin":30, "targetRepsMax":60,
--    "restSec":60, "note":"目标是秒数"}
--                                       ^^^^^^^^^^^^^^^^
--   靠一句中文注释提示「这里的 30/60 不是次数」。
--
-- 后端没有任何一行代码知道这件事（秒数只被原样透传，不参与任何计算），
-- 客户端也没有——于是跟练页对平板支撑**什么都不显示**，
-- 点「完成本组」时 duration_sec 传的是 null，这一组记成一条全空的记录。
-- AC-7-9「平板支撑单独统计总时长」因此一直拿不到数据。
--
-- 【为什么不给 prescribed_set 也加列】
--
-- 模板 JSON 里**没有 sets[] 数组**，所以从模板创建的计划，
-- prescribed_set 一行都没有（目标值全在 prescribed_exercise 上）。
-- 全库查证：prescribed_set 只有 1 行，是手动建的递增组。
--
-- 而计划编辑界面要等 M3 才做。所以这两列在 prescribed_set 上
-- **没有任何东西能写它**，却要挂在「updateStructure 全量删重建」
-- 这条最容易静默丢字段的路径上——纯负担。
-- M3 做编辑器时补一个 V15 DDL 即可，那时也没有历史数据要搬。
-- ============================================================


-- ------------------------------------------------------------
-- 1. DDL
--
-- 可空性照抄同表已有约定：
--   target_duration_sec     NULL —— 真正可选（卧推就没有目标时长），
--                          类比 target_reps_min
--   announce_interval_sec   处方表 NULL（NULL = 用默认）
--                           快照表 NOT NULL DEFAULT 10 —— 展开时已解析出
--                           确定值，类比 session_set_target.rest_sec
--                           （那列的注释写的就是「已解析」）
-- ------------------------------------------------------------

ALTER TABLE `prescribed_exercise`
    ADD COLUMN `target_duration_sec`   INT NULL
        COMMENT '目标持续时长（秒）。仅 DURATION / DISTANCE_DURATION 有意义' AFTER `target_reps_max`,
    ADD COLUMN `announce_interval_sec` INT NULL
        COMMENT '倒计时期间的播报间隔（秒），0 = 不间隔播报。NULL = 用默认' AFTER `target_duration_sec`;

ALTER TABLE `session_set_target`
    ADD COLUMN `target_duration_sec`   INT NULL
        COMMENT '目标持续时长（秒）。快照，展开时已解析' AFTER `target_reps_max`,
    ADD COLUMN `announce_interval_sec` INT NOT NULL DEFAULT 10
        COMMENT '播报间隔（秒），0 = 不间隔播报。快照，展开时已解析' AFTER `target_duration_sec`;


-- ------------------------------------------------------------
-- 2. 数据搬迁：把塞在 reps 字段里的秒数搬进新字段
--
-- 【关键前提：prescribed_exercise 才是数据所在】
--
-- 模板 JSON 是动作级的（targetSets + targetRepsMin/Max），
-- buildPrescription 遍历的是不存在的 sets[]，产出空 list，
-- insertStructure 里 `if (!isEmpty(p.sets()))` 整段跳过。
-- 所以 prescribed_set 从来就没有这些数据。
--
-- 【为什么倒计时取 repsMin 而不是 repsMax】
--
-- 「平板支撑 30-60 秒」= 至少撑 30 秒，争取 60。
-- 倒计时从**下限** 30 开始数：数到 0 即达成目标，
-- 继续撑下去的部分由客户端的「转正计时」承接成「已超 N 秒」——正反馈。
--
-- 若从 60 开始数，用户撑到 30 秒（已达标）时屏幕还写着「还剩 30 秒」，
-- 看起来像还差得远，是负反馈。
--
-- 【为什么把 reps 列清掉而不是留着】
--
-- 不清的话，SetTarget.repsLabel 仍然返回 "30-60"，
-- 跟练页目标卡、休息页的下一组预览、语音播报（会念「30 次」）、
-- 总结页的计划列——四处都要靠 metricType 分支去屏蔽。
-- 清掉之后 repsLabel 自然退化成「—」，语义干净。
-- 这个修正不可逆，但它纠正的本来就是建模错误：
-- 这两列里存的**从来不是次数**。
-- ------------------------------------------------------------

UPDATE `prescribed_exercise` pe
    JOIN `exercise` e ON e.id = pe.exercise_id
SET pe.`target_duration_sec`   = COALESCE(pe.`target_reps_min`, pe.`target_reps_max`),
    pe.`announce_interval_sec` = 10,
    pe.`target_reps_min`       = NULL,
    pe.`target_reps_max`       = NULL
WHERE e.`metric_type` IN ('DURATION', 'DISTANCE_DURATION')
  AND (pe.`target_reps_min` IS NOT NULL OR pe.`target_reps_max` IS NOT NULL);


-- 会话快照同样要搬。
--
-- 这里改的是**历史记录**，需要说明理由：次数字段里存的**本来就是秒数**，
-- 只是标注错了。这不是篡改历史，而是把一直存在的事实搬到正确的列上——
-- 搬完之后历史总结页才能显示「计划 30 秒 / 实际 45 秒」。
--
-- 只有 IN_PROGRESS 的会话会被跟练页读到，但历史会话一并搬，
-- 这样总结页对所有会话都一致。
UPDATE `session_set_target` t
    JOIN `session_exercise` se ON se.id = t.session_exercise_id
SET t.`target_duration_sec`   = COALESCE(t.`target_reps_min`, t.`target_reps_max`),
    t.`announce_interval_sec` = 10,
    t.`target_reps_min`       = NULL,
    t.`target_reps_max`       = NULL
WHERE se.`metric_type` IN ('DURATION', 'DISTANCE_DURATION')
  AND (t.`target_reps_min` IS NOT NULL OR t.`target_reps_max` IS NOT NULL);


-- ------------------------------------------------------------
-- 3. 修模板 JSON
--
-- ⚠️⚠️ 这一节踩了一个真实的坑，写下来免得下次再踩：
--
-- 【为什么不能用 V9 源文件里的字面子串做 REPLACE】
--
-- 最直觉的写法是拿 V9 里那行原文去替换：
--     REPLACE(structure, '"targetRepsMin":30,"targetRepsMax":60', ...)
--
-- **这个字符串在数据库里一次都不出现**（实测 LOCATE 返回 0）。原因两条：
--
--   1. MySQL 的 JSON 列是**二进制格式**，读出时会**重排 key**——
--      按「key 长度，再按字节序」排。targetRepsMax 和 targetRepsMin
--      都是 13 字节，比到第 12 个字符 'a' < 'i'，所以 **Max 排在 Min 前面**。
--   2. 渲染时 key 和值之间**带空格**：`"targetRepsMax": 60`。
--
-- 于是 REPLACE 匹配 0 行、**静默成功**，Flyway 报告迁移通过，
-- 而模板 JSON 根本没改。后面「从模板建计划」拿不到 targetDurationSec，
-- 跟练页就没有倒计时——全链路没有任何一层会报错。
--
-- 【所以改用 JSON_SET + 数组下标】
--
-- 下标看似脆弱，但 V9 是**冻结的**（Flyway checksum 不允许改），
-- 种子数据在任何环境都完全一致。而且下面的 WHERE 里用动作名做了守卫：
-- 一旦下标指向的不是平板支撑，UPDATE 影响 0 行，**不会误改别的动作**。
--
-- 已实测确认（按名字验证，不是靠数数）：
--   BODYWEIGHT_3DAY    $.days[0].exercises[4] → 平板支撑 max=60 min=30
--   BODYWEIGHT_BEGINNER $.days[0].exercises[3] → 平板支撑 max=40 min=20
-- ------------------------------------------------------------

-- 3.1 BODYWEIGHT_3DAY —— 全身 A（推为主）第 5 个动作
UPDATE `program_template`
SET `structure` = JSON_SET(
        JSON_REMOVE(`structure`,
                    '$.days[0].exercises[4].targetRepsMin',
                    '$.days[0].exercises[4].targetRepsMax'),
        '$.days[0].exercises[4].targetDurationSec',   30,
        '$.days[0].exercises[4].announceIntervalSec', 10)
WHERE `code` = 'BODYWEIGHT_3DAY'
  -- 守卫：下标必须真的指向平板支撑，否则一行都不改
  AND JSON_UNQUOTE(JSON_EXTRACT(`structure`, '$.days[0].exercises[4].exerciseName')) = '平板支撑';

-- 3.2 BODYWEIGHT_BEGINNER —— 第 4 个动作
UPDATE `program_template`
SET `structure` = JSON_SET(
        JSON_REMOVE(`structure`,
                    '$.days[0].exercises[3].targetRepsMin',
                    '$.days[0].exercises[3].targetRepsMax'),
        '$.days[0].exercises[3].targetDurationSec',   20,
        '$.days[0].exercises[3].announceIntervalSec', 10)
WHERE `code` = 'BODYWEIGHT_BEGINNER'
  AND JSON_UNQUOTE(JSON_EXTRACT(`structure`, '$.days[0].exercises[3].exerciseName')) = '平板支撑';


-- ------------------------------------------------------------
-- 4. 断言：改不动就让迁移失败，不要静默通过
--
-- 上一节所有语句「影响 0 行」都是合法的，Flyway 会照样报成功。
-- 这一步把「模板没长出 targetDurationSec」变成**硬失败**，
-- 让问题在启动时就暴露，而不是等到用户发现平板支撑没有倒计时。
--
-- 手法说明：MySQL 在纯脚本里不能直接 SIGNAL（那只在存储过程里可用），
-- 所以用 `SELECT 1 UNION ALL SELECT 2` 造一个「子查询返回多行」错误 1242。
-- IF 的条件成立时走前面那个 1，不成立时才踩到错误。
-- ------------------------------------------------------------

-- ⚠️ 用 JSON_CONTAINS_PATH 而不是 `JSON_EXTRACT(...) IS NOT NULL`：
-- 路径带 [*] 通配时 JSON_EXTRACT 返回的是**数组**（没匹配到就是 `[]`），
-- 永远不为 NULL——那个写法会恒真，等于没有断言。
SELECT IF(
    (SELECT COUNT(*) FROM `program_template`
     WHERE `code` = 'BODYWEIGHT_3DAY'
       AND JSON_CONTAINS_PATH(`structure`, 'one', '$.days[0].exercises[4].targetDurationSec')
       AND JSON_CONTAINS_PATH(`structure`, 'one', '$.days[0].exercises[4].announceIntervalSec')
       AND NOT JSON_CONTAINS_PATH(`structure`, 'one', '$.days[0].exercises[4].targetRepsMax')) = 1
    AND
    (SELECT COUNT(*) FROM `program_template`
     WHERE `code` = 'BODYWEIGHT_BEGINNER'
       AND JSON_CONTAINS_PATH(`structure`, 'one', '$.days[0].exercises[3].targetDurationSec')
       AND JSON_CONTAINS_PATH(`structure`, 'one', '$.days[0].exercises[3].announceIntervalSec')
       AND NOT JSON_CONTAINS_PATH(`structure`, 'one', '$.days[0].exercises[3].targetRepsMax')) = 1,
    1,
    (SELECT 1 UNION ALL SELECT 2)
) AS `v14_assert_templates_updated`;
