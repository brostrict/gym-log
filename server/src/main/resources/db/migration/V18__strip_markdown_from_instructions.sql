-- ============================================================
-- V18 —— 去掉动作要领里的 Markdown 标记
--
-- 【问题】
--
-- `V10` 给 41 个模板动作写要领时，我用了 `**短语**` 表示强调。
-- 动作库页面上线后，客户端直接把星号打了出来：
--
--   坐姿单腿前伸、身体前倾……**腿日之后最该做的一个**。
--
-- 【为什么不是「让客户端渲染 Markdown」】
--
-- 那是最容易想到的修法，但它是错的——**格式不该存在数据里**。
--
-- `**` 是 Markdown 的语法。存进 DB 之后，每一个消费者都要各自实现一遍渲染：
-- Flutter 端、Phase 5 的 Vue 端、以后的导出功能、以及直接查库的人。
-- 而「一份规则实现 N 遍」正是这个项目一直在避免的事
-- （同一类问题：e1RM 公式在 SQL 和 Java 里各一份，靠契约测试守着；
--  组数口径散落五处，靠 `TrainingMetrics.isWorkingSet` 收拢）。
--
-- 而且这条数据本身也不该带格式：`common_mistakes` 同样是要领类文本，
-- 它就没有标记。同一类字段两种写法，只会让人猜「这里到底支不支持 Markdown」。
--
-- **强调靠措辞，不靠标记。**
--
-- 【为什么 V17 是改源码、V10 是加迁移】
--
-- V17 是同一批工作里刚写的、**还没提交**的迁移，直接改源码 + 本地重放最干净。
--
-- V10 已经应用很久了，改它会 checksum 失败——迁移是只追加的历史记录
-- （DEV-LOG 踩坑 21）。所以这里用 REPLACE 清理存量。
--
-- 对全新环境：V17 插的就是干净文本，本迁移是 no-op。两种情况都对。
-- ============================================================

UPDATE `exercise`
SET `instructions` = REPLACE(`instructions`, '**', '')
WHERE `instructions` LIKE '%**%';

UPDATE `exercise`
SET `common_mistakes` = REPLACE(`common_mistakes`, '**', '')
WHERE `common_mistakes` LIKE '%**%';


-- ------------------------------------------------------------
-- 断言：一条都不剩
-- ------------------------------------------------------------
--
-- 「UPDATE 影响 0 行」在 Flyway 里是合法成功——
-- 如果 REPLACE 因为拼写问题没生效，Flyway 照样报通过，
-- 而界面上星号照旧。跟一条会失败的查询（DEV-LOG 踩坑 43）。

SELECT IF(
    (SELECT COUNT(*) FROM `exercise`
     WHERE `instructions` LIKE '%**%' OR `common_mistakes` LIKE '%**%') = 0,
    1,
    (SELECT 1 UNION ALL SELECT 2)
) AS `v18_assert_no_markdown_left`;
