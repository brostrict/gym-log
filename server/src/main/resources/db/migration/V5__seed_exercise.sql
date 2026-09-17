-- ============================================================
-- V5 —— 内置动作库种子数据
--
-- 共 88 个动作，覆盖 6 大肌群 + 有氧，足以支撑 6 个内置计划模板
-- （力量 / 分化 / 徒手三类）。
--
-- ⚠️ 关于「种子数据用不用 Flyway」的权衡：
--
--   用 Flyway 的好处：版本化、自动应用、各环境一致。
--
--   潜在冲突：Phase 6 的管理后台允许管理员改动作，
--   而迁移脚本只在首次执行时插入——之后管理员改了数据，
--   Flyway 不会（也不该）把它改回去。所以两者不冲突：
--   **迁移负责「首次播种」，之后数据归管理员管。**
--
--   如果将来要批量更新内置动作，新建 V6、V7 用 UPDATE 语句，
--   并在 where 条件里精确限定范围，避免覆盖管理员的修改。
-- ============================================================

-- ------------------------------------------------------------
-- 胸 —— 14 个
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`)
VALUES (0, '杠铃卧推', '卧推,bench press,平板卧推', 'CHEST', 'SHOULDERS,ARMS', 'BARBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 10),
       (0, '上斜杠铃卧推', '上斜卧推,incline bench press', 'CHEST', 'SHOULDERS,ARMS', 'BARBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 11),
       (0, '下斜杠铃卧推', '下斜卧推,decline bench press', 'CHEST', 'ARMS', 'BARBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 12),
       (0, '哑铃卧推', 'dumbbell bench press', 'CHEST', 'SHOULDERS,ARMS', 'DUMBBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 13),
       (0, '上斜哑铃卧推', 'incline dumbbell press', 'CHEST', 'SHOULDERS,ARMS', 'DUMBBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 14),
       (0, '哑铃飞鸟', '飞鸟,dumbbell fly', 'CHEST', 'SHOULDERS', 'DUMBBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 15),
       (0, '器械推胸', '坐姿推胸,chest press machine', 'CHEST', 'SHOULDERS,ARMS', 'MACHINE', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 16),
       (0, '蝴蝶机夹胸', '夹胸,pec deck', 'CHEST', 'SHOULDERS', 'MACHINE', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 17),
       (0, '绳索夹胸', '龙门架夹胸,cable crossover', 'CHEST', 'SHOULDERS', 'CABLE', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 18),
       (0, '俯卧撑', 'push up,pushup', 'CHEST', 'SHOULDERS,ARMS,CORE', 'BODYWEIGHT', 'HORIZONTAL_PUSH',
        'REPS_ONLY', 0.65, 0, 1, 19),
       (0, '上斜俯卧撑', '手高俯卧撑,incline push up', 'CHEST', 'SHOULDERS,ARMS', 'BODYWEIGHT', 'HORIZONTAL_PUSH',
        'REPS_ONLY', 0.45, 0, 1, 20),
       (0, '下斜俯卧撑', '脚高俯卧撑,decline push up', 'CHEST', 'SHOULDERS,ARMS', 'BODYWEIGHT', 'HORIZONTAL_PUSH',
        'REPS_ONLY', 0.75, 0, 1, 21),
       (0, '双杠臂屈伸', 'dips,双杠撑', 'CHEST', 'ARMS,SHOULDERS', 'BODYWEIGHT', 'HORIZONTAL_PUSH',
        'REPS_ONLY', 1.00, 0, 1, 22),
       (0, '哑铃仰卧屈臂上拉', 'pullover,哑铃上拉', 'CHEST', 'BACK,ARMS', 'DUMBBELL', 'HORIZONTAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 23);

-- ------------------------------------------------------------
-- 背 —— 16 个
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`)
VALUES (0, '引体向上', 'pull up,引体', 'BACK', 'ARMS,SHOULDERS', 'BODYWEIGHT', 'VERTICAL_PULL',
        'REPS_ONLY', 1.00, 0, 1, 30),
       (0, '高位下拉', 'lat pulldown,下拉', 'BACK', 'ARMS', 'CABLE', 'VERTICAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 31),
       (0, '窄距高位下拉', 'close grip pulldown', 'BACK', 'ARMS', 'CABLE', 'VERTICAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 32),
       (0, '杠铃划船', 'barbell row,俯身划船', 'BACK', 'ARMS,SHOULDERS', 'BARBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 33),
       (0, '哑铃单臂划船', 'one arm dumbbell row,单臂划船', 'BACK', 'ARMS', 'DUMBBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 1, 1, 34),
       (0, '坐姿绳索划船', 'seated cable row,坐姿划船', 'BACK', 'ARMS', 'CABLE', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 35),
       (0, 'T杠划船', 'T bar row', 'BACK', 'ARMS', 'BARBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 36),
       (0, '器械划船', 'seated row machine', 'BACK', 'ARMS', 'MACHINE', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 37),
       (0, '直臂下压', 'straight arm pulldown', 'BACK', 'CORE', 'CABLE', 'VERTICAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 38),
       (0, '反向划船', 'inverted row,澳式引体', 'BACK', 'ARMS,CORE', 'BODYWEIGHT', 'HORIZONTAL_PULL',
        'REPS_ONLY', 0.55, 0, 1, 39),
       (0, '硬拉', 'deadlift,传统硬拉', 'BACK', 'LEGS,CORE', 'BARBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 30),
       (0, '罗马尼亚硬拉', 'RDL,直腿硬拉', 'BACK', 'LEGS', 'BARBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 31),
       (0, '山羊挺身', 'back extension,背屈伸', 'BACK', 'LEGS', 'BODYWEIGHT', 'HINGE',
        'REPS_ONLY', 0.40, 0, 1, 32),
       (0, '面拉', 'face pull', 'BACK', 'SHOULDERS', 'CABLE', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 33),
       (0, '杠铃耸肩', 'shrug,耸肩', 'BACK', NULL, 'BARBELL', NULL,
        'WEIGHT_REPS', NULL, 0, 1, 34),
       (0, '哑铃耸肩', 'dumbbell shrug', 'BACK', NULL, 'DUMBBELL', NULL,
        'WEIGHT_REPS', NULL, 0, 1, 35);

-- ------------------------------------------------------------
-- 腿 —— 18 个
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`)
VALUES (0, '杠铃深蹲', '深蹲,squat,颈后深蹲', 'LEGS', 'CORE', 'BARBELL', 'SQUAT',
        'WEIGHT_REPS', NULL, 0, 1, 10),
       (0, '前蹲', 'front squat', 'LEGS', 'CORE', 'BARBELL', 'SQUAT',
        'WEIGHT_REPS', NULL, 0, 1, 11),
       (0, '高脚杯深蹲', 'goblet squat,哑铃深蹲', 'LEGS', 'CORE', 'DUMBBELL', 'SQUAT',
        'WEIGHT_REPS', NULL, 0, 1, 12),
       (0, '腿举', 'leg press,倒蹬', 'LEGS', NULL, 'MACHINE', 'SQUAT',
        'WEIGHT_REPS', NULL, 0, 1, 13),
       (0, '哈克深蹲', 'hack squat', 'LEGS', NULL, 'MACHINE', 'SQUAT',
        'WEIGHT_REPS', NULL, 0, 1, 14),
       (0, '腿屈伸', 'leg extension,坐姿腿屈伸', 'LEGS', NULL, 'MACHINE', 'SQUAT',
        'WEIGHT_REPS', NULL, 0, 1, 15),
       (0, '腿弯举', 'leg curl,俯卧腿弯举', 'LEGS', NULL, 'MACHINE', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 16),
       (0, '保加利亚分腿蹲', 'bulgarian split squat,单腿蹲', 'LEGS', 'CORE', 'DUMBBELL', 'LUNGE',
        'WEIGHT_REPS', NULL, 1, 1, 17),
       (0, '行走箭步蹲', 'walking lunge,箭步走', 'LEGS', 'CORE', 'DUMBBELL', 'LUNGE',
        'WEIGHT_REPS', NULL, 1, 1, 18),
       (0, '箭步蹲', 'lunge,弓步蹲', 'LEGS', 'CORE', 'BODYWEIGHT', 'LUNGE',
        'REPS_ONLY', 0.85, 1, 1, 19),
       (0, '哑铃罗马尼亚硬拉', 'dumbbell RDL', 'LEGS', 'BACK', 'DUMBBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 20),
       (0, '臀桥', 'glute bridge,桥式', 'LEGS', 'CORE', 'BARBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 21),
       (0, '臀冲', 'hip thrust', 'LEGS', 'CORE', 'BARBELL', 'HINGE',
        'WEIGHT_REPS', NULL, 0, 1, 22),
       (0, '单腿臀桥', 'single leg glute bridge', 'LEGS', 'CORE', 'BODYWEIGHT', 'HINGE',
        'REPS_ONLY', 0.50, 1, 1, 23),
       (0, '站姿提踵', 'standing calf raise,提踵', 'LEGS', NULL, 'MACHINE', 'CALF_RAISE',
        'WEIGHT_REPS', NULL, 0, 1, 24),
       (0, '坐姿提踵', 'seated calf raise', 'LEGS', NULL, 'MACHINE', 'CALF_RAISE',
        'WEIGHT_REPS', NULL, 0, 1, 25),
       (0, '深蹲跳', 'jump squat,蹲跳', 'LEGS', 'CORE', 'BODYWEIGHT', 'SQUAT',
        'REPS_ONLY', 0.90, 0, 1, 26),
       (0, '箱子深蹲', 'box squat,箱蹲', 'LEGS', 'CORE', 'BODYWEIGHT', 'SQUAT',
        'REPS_ONLY', 0.85, 0, 1, 27);

-- ------------------------------------------------------------
-- 肩 —— 12 个
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`)
VALUES (0, '站姿杠铃推举', 'overhead press,OHP,军事推举', 'SHOULDERS', 'ARMS,CORE', 'BARBELL', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 10),
       (0, '坐姿哑铃推举', 'dumbbell shoulder press', 'SHOULDERS', 'ARMS', 'DUMBBELL', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 11),
       (0, '阿诺德推举', 'arnold press', 'SHOULDERS', 'ARMS', 'DUMBBELL', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 12),
       (0, '器械推举', 'shoulder press machine', 'SHOULDERS', 'ARMS', 'MACHINE', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 0, 1, 13),
       (0, '哑铃侧平举', 'lateral raise,侧平举,飞鸟', 'SHOULDERS', NULL, 'DUMBBELL', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 1, 1, 14),
       (0, '绳索侧平举', 'cable lateral raise', 'SHOULDERS', NULL, 'CABLE', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 1, 1, 15),
       (0, '哑铃前平举', 'front raise,前平举', 'SHOULDERS', NULL, 'DUMBBELL', 'VERTICAL_PUSH',
        'WEIGHT_REPS', NULL, 1, 1, 16),
       (0, '反向飞鸟', 'rear delt fly,后束飞鸟', 'SHOULDERS', 'BACK', 'DUMBBELL', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 17),
       (0, '绳索面拉（肩）', 'rear delt cable', 'SHOULDERS', 'BACK', 'CABLE', 'HORIZONTAL_PULL',
        'WEIGHT_REPS', NULL, 0, 1, 18),
       (0, '倒立撑', 'handstand push up', 'SHOULDERS', 'ARMS,CORE', 'BODYWEIGHT', 'VERTICAL_PUSH',
        'REPS_ONLY', 0.90, 0, 1, 19),
       (0, '靠墙倒立撑', 'wall handstand push up', 'SHOULDERS', 'ARMS', 'BODYWEIGHT', 'VERTICAL_PUSH',
        'REPS_ONLY', 0.60, 0, 1, 20),
       (0, '派克俯卧撑', 'pike push up,屈体俯卧撑', 'SHOULDERS', 'ARMS', 'BODYWEIGHT', 'VERTICAL_PUSH',
        'REPS_ONLY', 0.70, 0, 1, 21);

-- ------------------------------------------------------------
-- 手臂 —— 14 个
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`)
VALUES (0, '杠铃弯举', 'barbell curl,二头弯举', 'ARMS', NULL, 'BARBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 10),
       (0, '哑铃交替弯举', 'alternating dumbbell curl', 'ARMS', NULL, 'DUMBBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 1, 1, 11),
       (0, '锤式弯举', 'hammer curl,锤式', 'ARMS', NULL, 'DUMBBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 1, 1, 12),
       (0, '牧师凳弯举', 'preacher curl,斜托弯举', 'ARMS', NULL, 'BARBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 13),
       (0, '绳索弯举', 'cable curl', 'ARMS', NULL, 'CABLE', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 14),
       (0, '反向弯举', 'reverse curl,反握弯举', 'ARMS', NULL, 'BARBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 15),
       (0, '腕弯举', 'wrist curl', 'ARMS', NULL, 'DUMBBELL', 'ELBOW_FLEXION',
        'WEIGHT_REPS', NULL, 0, 1, 16),
       (0, '窄距卧推', 'close grip bench press,窄推', 'ARMS', 'CHEST,SHOULDERS', 'BARBELL', 'ELBOW_EXTENSION',
        'WEIGHT_REPS', NULL, 0, 1, 17),
       (0, '绳索下压', 'tricep pushdown,三头下压', 'ARMS', NULL, 'CABLE', 'ELBOW_EXTENSION',
        'WEIGHT_REPS', NULL, 0, 1, 18),
       (0, '仰卧臂屈伸', 'skull crusher,碎颅者', 'ARMS', NULL, 'BARBELL', 'ELBOW_EXTENSION',
        'WEIGHT_REPS', NULL, 0, 1, 19),
       (0, '哑铃颈后臂屈伸', 'overhead tricep extension', 'ARMS', NULL, 'DUMBBELL', 'ELBOW_EXTENSION',
        'WEIGHT_REPS', NULL, 0, 1, 20),
       (0, '凳上反屈伸', 'bench dip,椅上臂屈伸', 'ARMS', 'CHEST', 'BODYWEIGHT', 'ELBOW_EXTENSION',
        'REPS_ONLY', 0.55, 0, 1, 21),
       (0, '窄距俯卧撑', 'diamond push up,钻石俯卧撑', 'ARMS', 'CHEST', 'BODYWEIGHT', 'ELBOW_EXTENSION',
        'REPS_ONLY', 0.70, 0, 1, 22),
       (0, '锤式绳索下压', 'rope pushdown', 'ARMS', NULL, 'CABLE', 'ELBOW_EXTENSION',
        'WEIGHT_REPS', NULL, 0, 1, 23);

-- ------------------------------------------------------------
-- 核心 —— 11 个
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`)
VALUES (0, '平板支撑', 'plank,平板撑', 'CORE', 'SHOULDERS', 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 0, 1, 10),
       (0, '侧平板支撑', 'side plank', 'CORE', NULL, 'BODYWEIGHT', 'CORE',
        'DURATION', NULL, 1, 1, 11),
       (0, '卷腹', 'crunch,仰卧卷腹', 'CORE', NULL, 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.35, 0, 1, 12),
       (0, '仰卧起坐', 'sit up', 'CORE', NULL, 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.40, 0, 1, 13),
       (0, '悬垂举腿', 'hanging leg raise,吊杠举腿', 'CORE', 'ARMS', 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.50, 0, 1, 14),
       (0, '俄罗斯转体', 'russian twist,转体', 'CORE', NULL, 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.35, 0, 1, 15),
       (0, '死虫', 'dead bug', 'CORE', NULL, 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.30, 0, 1, 16),
       (0, '登山者', 'mountain climber', 'CORE', 'SHOULDERS', 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.45, 0, 1, 17),
       (0, '绳索卷腹', 'cable crunch,跪姿卷腹', 'CORE', NULL, 'CABLE', 'CORE',
        'WEIGHT_REPS', NULL, 0, 1, 18),
       (0, '健腹轮', 'ab wheel,腹肌轮', 'CORE', 'SHOULDERS', 'BODYWEIGHT', 'CORE',
        'REPS_ONLY', 0.60, 0, 1, 19),
       (0, '负重行走', 'farmer walk,农夫行走', 'CORE', 'BACK,ARMS', 'DUMBBELL', 'CARRY',
        'WEIGHT_REPS', NULL, 0, 1, 20);

-- ------------------------------------------------------------
-- 有氧 —— 5 个
--
-- 注意 metric_type 是 DISTANCE_DURATION 或 DURATION，
-- 这两类**不计入训练容量**——距离和时长无法折算成「重量×次数」，
-- 单独统计。详见 MetricType 枚举里的说明。
-- ------------------------------------------------------------
INSERT INTO `exercise`
(`user_id`, `name`, `alias`, `primary_muscle`, `secondary_muscles`, `equipment`, `movement_pattern`,
 `metric_type`, `bw_factor`, `is_unilateral`, `status`, `sort_order`)
VALUES (0, '跑步', 'run,running,慢跑', 'LEGS', 'CORE', 'BODYWEIGHT', NULL,
        'DISTANCE_DURATION', NULL, 0, 1, 90),
       (0, '划船机', 'rowing machine,赛艇机', 'BACK', 'LEGS,ARMS', 'MACHINE', 'HORIZONTAL_PULL',
        'DISTANCE_DURATION', NULL, 0, 1, 91),
       (0, '动感单车', 'spinning,单车', 'LEGS', 'CORE', 'MACHINE', 'SQUAT',
        'DISTANCE_DURATION', NULL, 0, 1, 92),
       (0, '椭圆机', 'elliptical', 'LEGS', 'ARMS', 'MACHINE', NULL,
        'DISTANCE_DURATION', NULL, 0, 1, 93),
       (0, '跳绳', 'jump rope,skip', 'LEGS', 'CORE', 'BODYWEIGHT', NULL,
        'DURATION', NULL, 0, 1, 94);

-- ============================================================
-- 种子数据统计
-- ============================================================
SELECT primary_muscle AS `肌群`, COUNT(*) AS `动作数`
FROM `exercise`
WHERE `user_id` = 0
GROUP BY `primary_muscle`
ORDER BY `动作数` DESC;
