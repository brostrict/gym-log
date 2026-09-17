-- ============================================================
-- gym-log —— 数据库初始化
--
-- 这个脚本**只跑一次**，用来创建数据库本身（不建表）。
--
-- 为什么单独放这里而不是用 Flyway：
--   Flyway 是连到「某个已存在的数据库」里去建表的，
--   它没法创建数据库自己（鸡生蛋问题）。
--   所以「建库」这一步必须手动做，「建表」才交给 Flyway。
--
-- 执行方式（在项目根目录 server/ 下）：
--   mysql --login-path=gymlog < db/init.sql
--   或者进入 mysql 客户端后： source db/init.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS `gym_log`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 测试库单独一个，避免测试数据污染开发数据
CREATE DATABASE IF NOT EXISTS `gym_log_test`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 确认创建结果
SHOW DATABASES LIKE 'gym\_log%';
