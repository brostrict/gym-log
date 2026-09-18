-- ============================================================
-- V17 —— 动作库扩充：90 → 125
--
-- 起因：拿同功能的「训记」对照了一遍它的动作库（见 DEV-LOG 的竞品对照一节）。
--
-- 【补什么：内容广度，不是维度粒度】
--
-- 训记的粒度到了「器械型号 + 版本号」（悍马机推胸(版本2)、塔式上斜卧推、
-- 直立器械推胸(版本2)……）。那个不抄——它是给「同一个动作在不同健身房的
-- 机器上不一样」用的，靠 UGC + 内容团队撑起来，一个人做作品集项目
-- 铺到那个量级既没素材也没意义。
--
-- 抄的是缺口：我们的 90 个里，背只有「杠铃划船 / T杆划船 / 坐姿绳索划船」
-- 三种水平拉，而上背厚度的经典动作一个都没有。
--
-- 【两个新分类：WARMUP / STRETCH】
--
-- ⚠️ 它们不是肌群，只是借 `primary_muscle` 这个必填列住下来。
-- `MuscleGroup.isMuscle()` 把它们挡在统计之外——
-- 否则「拉伸」会出现在肌群周组数平衡图里，和「10–20 组/周」的
-- 增肌参考区间并列，而 6 组拉伸和 6 组深蹲毫无可比性。
--
-- 【为什么是新增迁移而不是改 V5】
--
-- V5 已经应用过了。Flyway 的已应用迁移不能改（checksum 会失败）——
-- 迁移是只追加的历史记录（DEV-LOG 踩坑 21）。
--
-- 【⚠️ instructions 里不要写 Markdown】
--
-- 第一版我在要领里写了 `**短语**` 表示强调，结果客户端直接把星号打了出来。
--
-- 根因不是「客户端没渲染 Markdown」，而是**格式不该存在数据里**：
-- `**` 是 Markdown 的语法，存进 DB 之后，每一个消费者
-- （Flutter、Phase 5 的 Vue、导出功能、直接查库的人）都要各自实现一遍渲染——
-- 而「一份格式规则实现 N 遍」正是这个项目一直在避免的事。
--
-- 强调靠措辞，不靠标记。
--
-- （V10 里已经有 15 条同样的问题，用 V18 清理。）
--
-- 【sort_order 用 100+ 段位】
--
-- 现有动作每个肌群占 10–94 的段。新的从 100 起，排在各自肌群已有动作之后，
-- 不打乱用户已经熟悉的顺序。
-- ============================================================


-- ------------------------------------------------------------
-- 胸 +3 —— 技巧变式与孤立收尾
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '暂停卧推', 'pause bench press,停顿卧推', 'CHEST', 'SHOULDERS,ARMS', 'BARBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 100,
        '杠铃触胸后停顿 1–2 秒再推起，消除借力、暴露 weakest point。重量要比常规卧推降 10–15%。'),
       (0, '宽距杠铃卧推', 'wide grip bench press', 'CHEST', 'SHOULDERS', 'BARBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 101,
        '握距比肩宽一掌以上，更多刺激胸大肌外侧。⚠️ 握太宽会加大肩关节压力，以无名指压在杠铃滚花环上为界。'),
       (0, '杠铃片夹胸', 'plate squeeze,片夹胸', 'CHEST', 'SHOULDERS', 'OTHER', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 102,
        '双手掌心相对夹住杠铃片，全程用力向内挤压。适合作为胸部训练的收尾，重量不重要、挤压感才重要。');


-- ------------------------------------------------------------
-- 背 +6 —— 本次最大的缺口：上背厚度
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '潘德雷划船', 'Pendlay row,触地划船', 'BACK', 'ARMS,SHOULDERS', 'BARBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 100,
        '每一组都从地面重新启动，躯干接近水平。比常规杠铃划船更强调上背爆发力，也更能避免借力。'),
       (0, '海豹划船', 'seal row,胸托划船', 'BACK', 'ARMS', 'BARBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 101,
        '俯卧在垫高的凳上，躯干完全固定、只有手臂动。彻底排除借力，是感受背阔肌发力最好的动作之一。'),
       (0, '俯卧杠铃划船', '俯卧划船,prone row', 'BACK', 'ARMS', 'BARBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 102,
        '俯卧在上斜凳上做划船，躯干贴紧凳面。和海豹划船的区别是凳面角度更斜、行程更长。'),
       (0, '地雷杆划船', 'landmine row,T杠划船单手', 'BACK', 'ARMS', 'OTHER', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 1, 1, 103,
        '杠铃一端固定在地雷架，单手拉。单侧训练便于发现左右力量差，行程也比双手划船更长。'),
       (0, '反手杠铃划船', 'underhand row,反握划船', 'BACK', 'ARMS', 'BARBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 104,
        '反握（掌心朝前）会显著增加肱二头肌参与，同时让背阔肌下部收缩更充分。'),
       (0, '半程硬拉', 'rack pull,架上硬拉', 'BACK', 'LEGS', 'BARBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 105,
        '把杠铃放在架上，从膝盖上方的位置起拉。行程短、能用更大重量，专练上背和斜方的锁握能力。');


-- ------------------------------------------------------------
-- 腿 / 后链 +4 —— 臀腿的第二个缺口
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '早安式', 'good morning,早安式体前屈', 'LEGS', 'BACK', 'BARBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 100,
        '杠铃扛在斜方肌上，保持背部挺直向前俯身。腘绳肌和臀大肌的孤立铰链动作，重量要比深蹲轻得多。'),
       (0, '地雷杆罗马尼亚硬拉', 'landmine RDL', 'LEGS', 'BACK', 'OTHER', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 101,
        '用 T 杠做罗马尼亚硬拉。杠铃走弧线，对下背更友好，适合腰部有旧伤的人。'),
       (0, '杠铃单腿硬拉', 'single leg RDL,单腿罗马尼亚硬拉', 'LEGS', 'CORE', 'BARBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 1, 1, 102,
        '单腿支撑做髋铰链，另一条腿向后伸展。同时练后链和平衡，重量只需双腿版的 40–50%。'),
       (0, '杠铃上凳', 'step up,登阶', 'LEGS', 'CORE', 'BARBELL', 'LUNGE',
        'WEIGHT_REPS', NULL, 1, 1, 103,
        '单脚踩上箱子再站起。箱子高度以大腿与地面平行为宜，过高会让膝盖承受不必要的剪切力。');


-- ------------------------------------------------------------
-- 手臂 +6 —— 二头变式 + 三头长头
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '蜘蛛弯举', 'spider curl', 'ARMS', NULL, 'DUMBBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 100,
        '俯卧在上斜凳上、手臂垂直下垂做弯举。重力方向与常规弯举差 90°，顶峰收缩最强烈的弯举变式。'),
       (0, '集中弯举', 'concentration curl', 'ARMS', NULL, 'DUMBBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 1, 1, 101,
        '坐姿、肘部抵住大腿内侧做单臂弯举。把肘固定死，是纠正借力最有效的孤立动作。'),
       (0, '拖式弯举', 'drag curl,拖拽弯举', 'ARMS', NULL, 'BARBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 102,
        '杠铃贴着身体向上「拖」，肘部向后移动。和常规弯举相反，全程保持张力、没有休息点。'),
       (0, 'EZ杆弯举', 'EZ bar curl,曲杠弯举', 'ARMS', NULL, 'BARBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 103,
        '曲杠的 W 形握位让手腕处于自然角度，比直杠更不容易引起腕关节不适。'),
       (0, '俯卧上斜弯举', 'incline curl,上斜哑铃弯举', 'ARMS', NULL, 'DUMBBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 104,
        '靠在上斜凳上、手臂垂在身后做弯举。拉长了肱二头肌长头，是这个动作唯一的存在理由。'),
       (0, '绳索过顶臂屈伸', 'overhead cable extension,过顶下压', 'ARMS', NULL, 'CABLE', 'ELBOW_EXTENSION',
        'WEIGHT_REPS', NULL, 0, 1, 105,
        '绳索从身后过顶下压。三头肌长头只有在手臂过顶时才被充分拉长，这是绳索下压给不了的。');


-- ------------------------------------------------------------
-- 肩 +1
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '站姿哑铃推举', 'standing dumbbell press', 'SHOULDERS', 'ARMS,CORE', 'DUMBBELL', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 100,
        '站姿推举比坐姿多一层核心稳定需求。⚠️ 不要为了推起更大重量而后仰——那会把动作变成上斜卧推。');


-- ------------------------------------------------------------
-- 核心 +3
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '侧卷腹', 'side crunch,侧向卷腹', 'CORE', NULL, 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.35, 0, 1, 100,
        '侧卧做单侧卷腹，练腹斜肌。⚠️ 双手不要抱头用力拉脖子，扶耳侧即可。'),
       (0, '鸟狗式', 'bird dog,四足伸展', 'CORE', 'BACK', 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.30, 0, 1, 101,
        '四足跪姿，对侧手脚同时伸出并保持 2–3 秒。练的是抗旋转稳定，不是腹肌围度——动作慢才有效。'),
       (0, 'V字两头起', 'V-up,两头起', 'CORE', NULL, 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.45, 0, 1, 102,
        '仰卧同时抬起上身和双腿，手去碰脚。对上腹要求高，做不动时先做屈膝版本。');


-- ------------------------------------------------------------
-- 热身 +6（新分类）
-- ------------------------------------------------------------
--
-- 全部是 DURATION：热身没有「重量 × 次数」。
-- 单位是秒——用户按「撑 60 秒」的方式记录，和跟练页的时长类动作走同一条路径。

INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '开合跳', 'jumping jack', 'WARMUP', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 0, 1, 100,
        '全身性升温，60 秒就能把心率拉起来。训练前做 2–3 组，比静态拉伸更能降低受伤风险。'),
       (0, '原地高抬腿', 'high knees', 'WARMUP', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 0, 1, 101,
        '快速交替抬腿至髋高。主要作用是升温和激活髋屈肌，为深蹲类动作做准备。'),
       (0, '肩关节绕环', 'shoulder circle,肩绕环', 'WARMUP', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 0, 1, 102,
        '手臂伸直做大圈绕环，前后各一组。推类训练前必做——肩袖没热开就上大重量是肩伤的头号原因。'),
       (0, '髋关节绕环', 'hip circle,髋绕环', 'WARMUP', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 0, 1, 103,
        '扶墙单腿抬起画圈，两侧各一组。深蹲、硬拉这类髋主导动作前的针对性热身。'),
       (0, '猫牛式', 'cat cow,猫式牛式', 'WARMUP', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 0, 1, 104,
        '四足跪姿交替拱背与塌腰。活动脊柱的每个节段，久坐人群做这个比静态拉伸更有效。'),
       (0, '臀桥激活', 'glute bridge activation', 'WARMUP', NULL, 'BODYWEIGHT', 'HINGE',
        'DURATION', NULL, 0, 1, 105,
        '仰卧屈膝顶髋并保持数秒。目的是唤醒臀大肌——臀肌不发力时，深蹲和硬拉的负荷会转移到下背。');


-- ------------------------------------------------------------
-- 拉伸 +6（新分类）
-- ------------------------------------------------------------
--
-- 同样全是 DURATION，单侧动作按两侧各计一组。
-- 训练后做，静态保持 30 秒以上才有意义——低于 20 秒几乎没有效果。

INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`, `instructions`)
VALUES (0, '腘绳肌拉伸', 'hamstring stretch,大腿后侧拉伸', 'STRETCH', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 1, 1, 100,
        '坐姿单腿前伸、身体前倾，感受大腿后侧牵拉。腿日之后最该做的一个。'),
       (0, '股四头肌拉伸', 'quad stretch,大腿前侧拉伸', 'STRETCH', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 1, 1, 101,
        '站姿单手抓脚踝向臀部靠拢，膝盖并拢。久坐人群的股四头肌通常偏紧，会连带影响髋部姿态。'),
       (0, '髋屈肌拉伸', 'hip flexor stretch,弓步拉伸', 'STRETCH', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 1, 1, 102,
        '弓步跪姿、后腿髋部向前压。深蹲做不深的人多半是这里紧，而不是柔韧性不够。'),
       (0, '胸部拉伸', 'chest stretch,门框拉伸', 'STRETCH', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 1, 1, 103,
        '前臂抵住门框、身体前倾。对抗长期伏案的圆肩，推类训练后做。'),
       (0, '背阔肌拉伸', 'lat stretch,背阔拉伸', 'STRETCH', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 1, 1, 104,
        '双手抓握固定物、身体后坐下沉。拉类训练后做，能明显改善肩关节活动度。'),
       (0, '婴儿式', 'child pose,大拜式', 'STRETCH', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 0, 1, 105,
        '跪坐、上身向前趴、手臂前伸。同时放松下背和肩，适合作为整场训练的收尾。');


-- ------------------------------------------------------------
-- 断言：数量对得上
-- ------------------------------------------------------------
--
-- 「INSERT 影响 0 行」在 Flyway 里是合法成功——
-- 上面的语句因为拼写错误一条没插进去的话，Flyway 照样报通过。
-- 所以跟一条会失败的查询（DEV-LOG 踩坑 43）。

SELECT IF(
    (SELECT COUNT(*) FROM `exercise` WHERE `user_id` = 0) = 125,
    1,
    (SELECT 1 UNION ALL SELECT 2)
) AS `v17_assert_exercise_count`;

SELECT IF(
    (SELECT COUNT(*) FROM `exercise`
     WHERE `user_id` = 0 AND `primary_muscle` IN ('WARMUP', 'STRETCH')) = 12,
    1,
    (SELECT 1 UNION ALL SELECT 2)
) AS `v17_assert_new_categories`;
