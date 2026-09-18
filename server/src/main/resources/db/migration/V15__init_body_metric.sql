-- ============================================================
-- V15 —— 身体数据（M6：体重 / 体成分 / 生理指标 / 主观状态）
--
--   body_metric   一次测量：某个指标、某个部位、在某个时刻的一个数值
--
-- 【为什么是窄表，而 set_record 是宽表】
--
-- 同一个项目里两种相反的建模，必须说清楚区别，否则看起来就是不一致：
--
--   set_record    宽表 + 判别器（weight/reps/duration_sec/distance_m）
--   body_metric   窄表（metric_type + site + value，一个数一行）
--
-- 区别在两处，缺一不可：
--
--   ① 判别器是**封闭还是开放**
--      动作的计量类型只有四种，且是代码里定死的枚举——加一种要改算法，
--      不是加一列能解决的。所以宽表不会因为你「多了一个动作」而要 ALTER。
--      而身体指标是**开放**的：今天 14 个，明天想加「握力」「晨脉变异性」
--      就是加一个枚举值。宽表每加一个指标都要 ALTER TABLE。
--
--   ② 一行里**同时有几个值**
--      set_record 的一条记录天然是多字段的（这一组推了 60kg × 8 次，歇了 90 秒），
--      拆成窄表的话查一次容量要 join 再透视。
--      body_metric 的一条记录就是**一个数**——「今天 72.4kg」。
--      没有任何需要透视的东西。
--
-- 所以 set_record 的注释里那句「用宽字段而不是 EAV」在这里不适用：
-- 那条论证针对的是「一行多个相关量」的记录，不是「一行一个标量」的记录。
--
-- ============================================================
-- 【三个必须这么写的地方，每一个都有代价】
--
-- ① site 是 NOT NULL DEFAULT 'NONE'，**不是 NULL**
--
--    ⚠️ MySQL 的 UNIQUE 索引把 NULL 当作**互不相等**——
--    `UNIQUE KEY (user_id, metric_type, site, measured_at)` 里
--    site 为 NULL 时，同一用户、同一指标、同一时刻**可以插进去任意多条**。
--
--    而 site 只在围度（12 个部位）和酸痛度（6 个肌群）上有值，
--    其余 12 个指标全是「没有部位」——如果 site 可空，
--    幂等键对这 12 个指标**全部失效**，而且不报任何错：
--    客户端重传一次体重，库里就多一条。
--
--    所以「没有部位」用一个真实的枚举值 NONE 表示，不是 NULL 也不是空串。
--    选 NONE 而不是空串，是因为本项目所有枚举都按 MyBatis 默认的
--    `name()` 存（SetType 存的就是 'WARMUP'）——用 NONE 就自然落进
--    这套约定，不需要为「空串 ↔ null」再写一个 TypeHandler。
--    代价只是这个哨兵值在库里看得见，而它本来就该看得见。
--
-- ② value 是 DECIMAL(6,2)，**不是 (5,2)**
--
--    (5,2) 的上界是 999.99，而 BMR 正常值就有 1200–2500 kcal，
--    直接溢出。全部指标里只有 BMR 会出界，取 (6,2) 一次覆盖。
--    用 (7,2) 也行，但没有理由为不存在的量级留空间。
--
-- ③ 列名是 measure_condition，**不是 condition**
--
--    CONDITION 是 MySQL 的**保留字**。叫 condition 的话，每一处
--    手写 SQL 都得加反引号，漏一处就是语法错误；MyBatis-Plus 生成的
--    SQL 也得靠 @TableField 打反引号——而那是**运行期**才会暴露的错。
--    换个名字的成本是零，所以换。
--    （对外 JSON 字段仍叫 condition，API 语义不受影响）
-- ============================================================

CREATE TABLE `body_metric`
(
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,

    `user_id`           BIGINT        NOT NULL,

    -- WEIGHT / BODY_FAT / MUSCLE_MASS / WATER_PCT / BMR / VISCERAL_FAT /
    -- CIRCUMFERENCE / RESTING_HR / SLEEP_QUALITY / SLEEP_HOURS /
    -- ENERGY_LEVEL / SORENESS / PRE_WORKOUT_STATE / STRESS_LEVEL
    --
    -- 存字符串而不是 TINYINT：加一个指标不用改数值映射表，
    -- 而且直接查库时一眼看得懂（`metric_type = 'WEIGHT'`）。
    `metric_type`       VARCHAR(24)   NOT NULL COMMENT '指标类型，见 BodyMetricType',

    -- ⚠️ NOT NULL DEFAULT 'NONE' —— 理由见文件头 ①。**不要改成可空列。**
    -- NONE = 该指标没有部位概念（体重、体脂、BMR……）
    -- 有值 = 围度的 12 个部位之一，或酸痛度的 6 个肌群之一
    `site`              VARCHAR(24)   NOT NULL DEFAULT 'NONE'
        COMMENT '部位/肌群；NONE 表示该指标无部位概念。见 BodySite',

    -- 所有指标都只有一个数，所以一列就够。
    -- 单位不存库——由 metric_type 决定（kg / % / cm / bpm / 分），
    -- 存单位会引入「同指标两种单位」这种没法收拾的状态。
    `value`             DECIMAL(6, 2) NOT NULL COMMENT '数值。量程由 metric_type 决定',

    -- 测量时刻。**由客户端上报**——离线记录时服务端不在场
    `measured_at`       DATETIME      NOT NULL COMMENT '测量时刻，由客户端上报',

    -- 测量条件：FASTED / POST_WORKOUT / BEFORE_BED / OTHER
    -- ⚠️ 列名不是 condition —— CONDITION 是 MySQL 保留字，理由见文件头 ③
    -- 不是体重专属：静息心率要求晨起测、血压受体位影响，都靠它
    `measure_condition` VARCHAR(16)   NULL
        COMMENT 'FASTED/POST_WORKOUT/BEFORE_BED/OTHER。见 MetricCondition',

    -- 体脂秤型号（M6-B-6）
    --
    -- 不同品牌生物电阻抗算法差异很大，同一人在两台秤上可能差 3–5 个百分点。
    -- 混着记会让趋势线出现**假跳变**，而用户会以为是自己的身体变了。
    -- 记下型号后，换秤时可以在图上标注断点。
    -- 对体脂秤产出的五项都有意义（BODY_FAT / MUSCLE_MASS / WATER_PCT / BMR / VISCERAL_FAT）
    `device`            VARCHAR(64)   NULL COMMENT '体脂秤型号，M6-B-6',

    `note`              VARCHAR(255)  NULL,

    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (`id`),

    -- ⚠️ 这个唯一键同时承担两个职责（和 set_record 一样）：
    --
    -- ① **数据约束**：同一个人、同一指标、同一部位、同一时刻只有一个值
    -- ② **离线同步的幂等键**：重放「记录今天的体重」走 UPSERT，
    --    重复上传不产生两条（AC-5-1）
    --
    -- **能生效的前提是 site 为 NOT NULL**（见文件头 ①）——
    -- 换成可空列的话这个索引对 12 个指标就是摆设。
    UNIQUE KEY `uk_body_metric_natural` (`user_id`, `metric_type`, `site`, `measured_at`),

    -- 画趋势图的查询形态固定是「某用户 + 某指标 + 时间区间」，
    -- 上面那个唯一键的前缀是 (user_id, metric_type, site, measured_at)，
    -- site 在中间会把时间区间切碎，所以另建一条不含 site 的。
    KEY `idx_body_metric_user_type_date` (`user_id`, `metric_type`, `measured_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='身体数据（一次测量一行）';
