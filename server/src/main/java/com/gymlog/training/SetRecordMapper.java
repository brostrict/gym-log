package com.gymlog.training;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** SetRecord 数据访问接口。 */
@Mapper
public interface SetRecordMapper extends BaseMapper<SetRecord> {

    /**
     * 某动作在**本次训练之前**的最佳估算 1RM。
     *
     * <h3>⚠️ 这条 SQL 里有第二份 e1RM 公式</h3>
     *
     * <p>权威实现是 {@link TrainingMetrics#e1rm}（Java，纯函数）。
     * 这里重复一次是因为「扫描全部历史取最大值」在 SQL 里是一次聚合，
     * 在 Java 里要把用户所有历史记录拉进内存——
     * 练一年就是上万行，而打开一次训练总结就要跑一遍。
     *
     * <p><b>两处必须一致。</b> 靠 {@code TrainingMetricsTest} 里的
     * 契约测试守住：同一批数据，SQL 算出的值和 Java 算出的值必须相等。
     * 改公式时那个测试会红。
     *
     * <h3>三条过滤条件，每条都对应 METRICS 3.3 的一条规则</h3>
     * <pre>
     *   sr.set_type &lt;&gt; 'WARMUP'   热身组不算
     *   sr.reps &lt;= 12            超过 12 次 Epley 严重高估，排除
     *   se.metric_type = WEIGHT_REPS  自重和时长类没有 e1RM 概念
     * </pre>
     *
     * <p>{@code started_at < before} 而不是「排除本次会话」：
     * 用户回看三个月前那场训练的总结时，「历史最好」必须是**那之前**的，
     * 否则后来练出的成绩会把当时的 PR 抹掉。
     */
    @Select("""
            <script>
            SELECT se.exercise_id            AS exerciseId,
                   MAX(sr.weight * (1 + sr.reps / 30)) AS bestE1rm
              FROM set_record sr
              JOIN session_exercise se ON se.id = sr.session_exercise_id
              JOIN workout_session  ws ON ws.id = se.session_id
             WHERE ws.user_id = #{userId}
               AND ws.deleted = 0
               AND ws.status = 'COMPLETED'
               AND se.metric_type = 'WEIGHT_REPS'
               AND sr.set_type &lt;&gt; 'WARMUP'
               AND sr.reps BETWEEN 1 AND 12
               AND sr.weight IS NOT NULL
               AND ws.started_at &lt; #{before}
               AND se.exercise_id IN
                   <foreach collection="exerciseIds" item="id" open="(" separator="," close=")">
                       #{id}
                   </foreach>
             GROUP BY se.exercise_id
            </script>
            """)
    List<ExerciseBestE1rm> bestE1rmBefore(@Param("userId") Long userId,
                                          @Param("exerciseIds") List<Long> exerciseIds,
                                          @Param("before") LocalDateTime before);

    /** {@link #bestE1rmBefore} 的投影 */
    record ExerciseBestE1rm(Long exerciseId, BigDecimal bestE1rm) {
    }
}
