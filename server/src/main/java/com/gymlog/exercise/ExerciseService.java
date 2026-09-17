package com.gymlog.exercise;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.dto.ExerciseQuery;
import com.gymlog.exercise.dto.ExerciseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 动作库业务逻辑。
 */
@Service
@RequiredArgsConstructor
public class ExerciseService {

    private final ExerciseMapper exerciseMapper;

    /**
     * 分页查询动作。
     *
     * @param currentUserId 当前登录用户，决定能看到哪些自定义动作
     */
    public IPage<ExerciseResponse> query(Long currentUserId, ExerciseQuery query) {
        LambdaQueryWrapper<Exercise> wrapper = buildWrapper(currentUserId, query);

        Page<Exercise> page = new Page<>(query.normalizedPage(), query.normalizedSize());
        IPage<Exercise> result = exerciseMapper.selectPage(page, wrapper);

        // convert 会保留分页信息（总数、总页数），只把记录逐条转换
        return result.convert(ExerciseResponse::from);
    }

    /**
     * 按 id 查询单个动作，**并校验可见性**。
     *
     * <p><b>为什么这个方法必须存在，而不是直接 {@code selectById}</b>：
     * 列表查询和单条查询是两套代码路径。列表加了可见性过滤条件，
     * 单条如果只按 id 查就漏了——**这是越权漏洞最常见的入口**。
     *
     * <p>攻击方式很直接：拿一个别人的自定义动作 id，调
     * {@code GET /api/v1/exercises/{id}}，就能看到别人的数据。
     *
     * <p><b>⚠️ 越权时返回「不存在」而不是「无权限」</b>：
     * <pre>
     *   ✗ 返回 403「无权限」 → 攻击者确认了「这个 id 是存在的」，
     *                          可以据此枚举出系统里有多少动作、id 范围多大
     *   ✓ 返回 404「不存在」 → 攻击者无法区分「不存在」和「别人的」，
     *                          拿不到任何额外信息
     * </pre>
     * 这个原则对**所有按 id 访问的资源**都适用
     * （训练记录、计划、照片……）。
     *
     * @throws BizException 动作不存在、已停用，或属于其他用户
     */
    public Exercise getVisibleById(Long currentUserId, Long id) {
        Exercise exercise = exerciseMapper.selectById(id);

        if (exercise == null || !exercise.isAvailable()) {
            throw new BizException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        // 内置动作所有人可见；自定义动作只有创建者可见
        if (!exercise.isBuiltIn() && !exercise.isOwnedBy(currentUserId)) {
            throw new BizException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        return exercise;
    }

    /**
     * 构造查询条件。
     *
     * <p><b>抽成独立方法是为了让「可见性规则」显眼</b>——
     * 这是整个查询里唯一有安全含义的部分，不该淹没在筛选条件堆里。
     */
    private LambdaQueryWrapper<Exercise> buildWrapper(Long currentUserId, ExerciseQuery query) {
        LambdaQueryWrapper<Exercise> wrapper = new LambdaQueryWrapper<>();

        // ============================================================
        // 可见性规则（安全相关，改动前务必想清楚）
        // ============================================================
        //
        //   内置动作（user_id = 0）  → 所有人可见
        //   自定义动作（user_id = N）→ 只有 N 本人可见
        //
        // ⚠️ 两个条件缺一不可：
        //   · 漏掉「或自己的」 → 用户看不到自己建的动作
        //   · 漏掉「限本人」   → 用户能看到别人的自定义动作（数据越权）
        //
        // 这里用 and(...or...) 包一层，是为了让这个 OR 条件和后面的
        // AND 条件正确组合。不包的话，wrapper 会生成
        //   WHERE user_id = 0 OR user_id = ? AND status = 1
        // 由于 AND 优先级高于 OR，实际等价于
        //   WHERE user_id = 0 OR (user_id = ? AND status = 1)
        // ——内置动作的 status 条件就失效了。
        //
        // 这类「SQL 优先级」导致的 bug 极难发现，因为单测时数据往往是干净的。
        wrapper.and(w -> w
                .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID)
                .or()
                .eq(Exercise::getUserId, currentUserId)
        );

        if (Boolean.TRUE.equals(query.getOnlyCustom())) {
            // 「我的动作」列表：只保留自己创建的
            wrapper.eq(Exercise::getUserId, currentUserId);
        }

        // 只返回启用的动作。停用的动作在历史训练记录里仍然可见
        // （那是另一条查询路径），但新计划里选不到。
        wrapper.eq(Exercise::getStatus, Exercise.STATUS_ENABLED);

        // ---------- 筛选条件 ----------
        wrapper.eq(query.getMuscle() != null, Exercise::getPrimaryMuscle, query.getMuscle());
        wrapper.eq(query.getEquipment() != null, Exercise::getEquipment, query.getEquipment());
        wrapper.eq(query.getPattern() != null, Exercise::getMovementPattern, query.getPattern());
        wrapper.eq(query.getMetricType() != null, Exercise::getMetricType, query.getMetricType());

        // ---------- 关键字搜索 ----------
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            // 同样要包一层 and：否则 OR 会和上面的筛选条件混在一起，
            // 导致「按肌群筛选」被绕过
            wrapper.and(w -> w
                    .like(Exercise::getName, keyword)
                    .or()
                    .like(Exercise::getAlias, keyword)
            );
        }

        // ---------- 排序 ----------
        // 先按肌群分组（让同肌群的动作排在一起），
        // 再按 sort_order（常见动作靠前），最后按 id 保证顺序稳定。
        //
        // ⚠️ 最后一定要有唯一的排序字段（id）。只按前两个排序的话，
        // 相同 sort_order 的记录在不同页之间顺序可能变化，
        // 导致「翻页时看到重复或遗漏的记录」。
        wrapper.orderByAsc(Exercise::getPrimaryMuscle)
                .orderByAsc(Exercise::getSortOrder)
                .orderByAsc(Exercise::getId);

        return wrapper;
    }
}
