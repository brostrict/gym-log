package com.gymlog.exercise;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动作数据访问接口。
 *
 * <p>目前 {@code BaseMapper} 提供的方法够用。
 * 后续会在这里加自定义查询：
 * <ul>
 *   <li>按肌群统计训练容量（Phase 4 的指标聚合，需要 JOIN set_record）</li>
 *   <li>动作使用频率排行（Phase 6 的管理端看板）</li>
 * </ul>
 * 这些是多表聚合，会用 XML 而不是注解——SQL 长了注解写不下，
 * 且 XML 支持动态条件（{@code <if>} / {@code <foreach>}）。
 */
@Mapper
public interface ExerciseMapper extends BaseMapper<Exercise> {
}
