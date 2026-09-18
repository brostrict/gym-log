package com.gymlog.stats;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统计聚合的 SQL —— **本项目里少见的「必须走 SQL」的地方**。
 *
 * <h3>为什么这两条不复用 {@code SessionSummaryService} 的 Java 批查</h3>
 *
 * <p>PR 看板按定义就是**全时段**的（{@code METRICS 7.1}「历史最高」）。
 * Java 批查要把该用户**全部历史**的组记录拉进内存——一个练了四年的用户
 * 大约 1.6 万行，而这里只需要每个动作的一行。
 *
 * <p>这正是 {@code SetRecordMapper.bestE1rmBefore} 当初走 SQL 的同一个理由
 * （那条注释写的是「扫全部历史取 MAX」）。本项目对「聚合走 Java 还是 SQL」
 * 的既有边界是：**有界区间走 Java 批查，无界全史走 SQL**。
 *
 * <h3>⚠️ 两条手写 SQL 的注意事项</h3>
 *
 * <p><b>1. 逻辑删除要自己写。</b>{@code workout_session} 有 {@code deleted} 列，
 * 而 MyBatis-Plus 的逻辑删除**只对 {@code LambdaQueryWrapper} 生效**——
 * 手写 SQL 漏了 {@code ws.deleted = 0} 会把已删会话算进 PR，且不报错。
 *
 * <p><b>2. {@code set_type <> 'WARMUP'} 是 {@code TrainingMetrics.isWorkingSet}
 * 的 SQL 副本。</b>SQL 没法调 Java，所以这是必然的分叉点，靠注释互指。
 * 目前全项目有三处热身判定在 SQL 里：这里、{@code bestE1rmBefore}、
 * {@code SessionSummaryService} 用的是 Java 版。
 *
 * <p><b>3. e1RM 公式这是第三份。</b>第一份在 {@code TrainingMetrics.e1rm}（Java），
 * 第二份在 {@code bestE1rmBefore}（SQL）。表达式**刻意和那两份逐字一致**，
 * 并且纳入 {@code SessionSummaryServiceTest.sqlE1rmMatchesJavaE1rm} 的契约测试——
 * 三份算出不同的数，用户会看到「PR 看板说 82.5，训练总结说 80」。
 */
@Mapper
public interface StatsMapper {

    /**
     * 每个动作的**最大重量** PR，以及最早达成的那次。
     *
     * <p>{@code ORDER BY weight DESC, started_at ASC} + {@code rn = 1}
     * 一起实现了 {@code METRICS 7.4} 的「同一数值多次达成 → **只保留最早一次**」。
     */
    @Select("""
            SELECT * FROM (
                SELECT se.exercise_id        AS exerciseId,
                       se.exercise_name      AS exerciseName,
                       sr.weight             AS value,
                       ws.started_at         AS achievedAt,
                       MIN(ws.started_at) OVER (PARTITION BY se.exercise_id) AS firstAt,
                       ROW_NUMBER() OVER (PARTITION BY se.exercise_id
                                          ORDER BY sr.weight DESC, ws.started_at ASC) AS rn
                FROM set_record sr
                JOIN session_exercise se ON se.id = sr.session_exercise_id
                JOIN workout_session ws  ON ws.id = se.session_id
                WHERE ws.user_id = #{userId}
                  AND ws.deleted = 0
                  AND ws.status = 'COMPLETED'
                  AND sr.set_type <> 'WARMUP'
                  AND sr.weight IS NOT NULL
            ) t WHERE t.rn = 1
            """)
    List<PrCandidate> maxWeightPerExercise(@Param("userId") Long userId);

    /**
     * 每个动作的**最佳 e1RM** PR，以及最早达成的那次。
     *
     * <p>过滤条件与 {@code bestE1rmBefore} / {@code METRICS 3.3} 完全一致：
     * 仅 {@code WEIGHT_REPS}（自重和时长类没有 1RM 概念）、{@code reps ≤ 12}
     * （Epley 在 12 次以上严重高估）。
     */
    @Select("""
            SELECT * FROM (
                SELECT se.exercise_id        AS exerciseId,
                       se.exercise_name      AS exerciseName,
                       sr.weight * (1 + sr.reps / 30) AS value,
                       ws.started_at         AS achievedAt,
                       MIN(ws.started_at) OVER (PARTITION BY se.exercise_id) AS firstAt,
                       ROW_NUMBER() OVER (PARTITION BY se.exercise_id
                                          ORDER BY sr.weight * (1 + sr.reps / 30) DESC,
                                                   ws.started_at ASC) AS rn
                FROM set_record sr
                JOIN session_exercise se ON se.id = sr.session_exercise_id
                JOIN workout_session ws  ON ws.id = se.session_id
                WHERE ws.user_id = #{userId}
                  AND ws.deleted = 0
                  AND ws.status = 'COMPLETED'
                  AND se.metric_type = 'WEIGHT_REPS'
                  AND sr.set_type <> 'WARMUP'
                  AND sr.weight IS NOT NULL
                  AND sr.reps BETWEEN 1 AND 12
            ) t WHERE t.rn = 1
            """)
    List<PrCandidate> bestE1rmPerExercise(@Param("userId") Long userId);

    /**
     * 一个 PR 候选。
     *
     * @param firstAt 该动作**第一次**有记录的会话开始时间。
     *                等于 {@code achievedAt} 时说明「第一次练就创了纪录」
     *                （{@code METRICS 7.4} 要求标「首次记录」且用中性样式）
     */
    record PrCandidate(
            Long exerciseId,
            String exerciseName,
            BigDecimal value,
            LocalDateTime achievedAt,
            LocalDateTime firstAt
    ) {
    }
}
