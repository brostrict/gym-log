package com.gymlog.exercise;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.dto.ExerciseQuery;
import com.gymlog.exercise.dto.ExerciseResponse;
import com.gymlog.exercise.dto.ExerciseSaveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 动作库业务逻辑。
 */
@Slf4j
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
     * 新建自定义动作。
     *
     * @param currentUserId 创建者，来自 token
     * @return 新动作的 id
     */
    @Transactional
    public Long create(Long currentUserId, ExerciseSaveRequest request) {
        Exercise exercise = new Exercise();

        // ⚠️ 归属由服务端决定，绝不接受客户端传入的 userId。
        // 否则用户可以伪造 userId=0 来「创建内置动作」。
        exercise.setUserId(currentUserId);

        applyRequest(exercise, request);
        exercise.setStatus(Exercise.STATUS_ENABLED);

        try {
            exerciseMapper.insert(exercise);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_exercise_user_name 命中：该用户已有同名动作。
            // 同注册接口的处理方式——应用层先查一次是为了友好提示，
            // 唯一索引才是真正的保证。
            throw new BizException(ErrorCode.EXERCISE_NAME_DUPLICATED);
        }

        log.info("创建自定义动作 | userId={} | id={} | name={}",
                currentUserId, exercise.getId(), exercise.getName());
        return exercise.getId();
    }

    /**
     * 编辑自定义动作。
     *
     * <p><b>用户可以改的只有自己创建的动作</b>——内置动作属于系统，
     * 只有管理员能改（Phase 6 的管理端）。
     */
    @Transactional
    public void update(Long currentUserId, Long id, ExerciseSaveRequest request) {
        Exercise existing = loadEditable(currentUserId, id);

        // 记录旧名称，方便日志排查
        String oldName = existing.getName();

        applyRequest(existing, request);

        try {
            exerciseMapper.updateById(existing);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.EXERCISE_NAME_DUPLICATED);
        }

        log.info("编辑自定义动作 | userId={} | id={} | {} -> {}",
                currentUserId, id, oldName, existing.getName());
    }

    /**
     * 删除自定义动作（逻辑删除）。
     *
     * <p><b>⚠️ 这里有个必须想清楚的问题：删掉的动作如果已经被计划引用了怎么办？</b>
     *
     * <p>本步骤先不做引用检查（计划功能还没实现）。
     * 等步骤 2.8 计划 CRUD 完成后，要补上「引用检查」：
     * <pre>
     *   被计划引用 → 拒绝删除，提示「该动作正被 N 个计划使用」
     *              （对应 ErrorCode.EXERCISE_IN_USE）
     * </pre>
     *
     * <p>另一个相关设计：**历史训练记录不受影响**。
     * 组记录里保存的是动作的**快照**（名称等），不是外键引用，
     * 所以删掉动作后，三个月前的训练记录仍然显示「杠铃卧推」。
     * 这是 REQUIREMENTS 6.3 里的「不变量 1」。
     */
    @Transactional
    public void delete(Long currentUserId, Long id) {
        Exercise existing = loadEditable(currentUserId, id);

        // MyBatis-Plus 的逻辑删除：实际执行 UPDATE exercise SET deleted=1 WHERE id=?
        // 所有查询会自动附加 AND deleted=0
        exerciseMapper.deleteById(id);

        log.info("删除自定义动作 | userId={} | id={} | name={}",
                currentUserId, id, existing.getName());
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 加载一个「当前用户可编辑的」动作，不满足条件直接抛异常。
     *
     * <p><b>把校验收在一个方法里，是为了避免散落各处导致遗漏。</b>
     * 编辑和删除都要做同样的判断，写两遍就容易只改一处。
     *
     * <p>两种情况合并成同一个错误码，理由同 {@link #getVisibleById}：
     * 不泄露「这个 id 存在」的信息。
     */
    private Exercise loadEditable(Long currentUserId, Long id) {
        Exercise exercise = exerciseMapper.selectById(id);

        if (exercise == null || !exercise.isAvailable()) {
            throw new BizException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        // 内置动作不可编辑——它是系统资产，改动会影响所有用户
        if (exercise.isBuiltIn()) {
            throw new BizException(ErrorCode.FORBIDDEN, "内置动作不能修改");
        }

        // 只能改自己的
        if (!exercise.isOwnedBy(currentUserId)) {
            throw new BizException(ErrorCode.EXERCISE_NOT_FOUND);
        }

        return exercise;
    }

    /**
     * 把请求里的字段应用到实体上，并做跨字段的规范化。
     *
     * <p><b>为什么要「规范化」而不是「校验后拒绝」</b>：
     * {@code bwFactor} 和 {@code metricType} 是联动的——
     * 负重动作不该有体重系数，自重动作必须有。
     *
     * <p>两种处理方式：
     * <pre>
     *   校验后拒绝 → 用户改计量类型时必须手动清空 bwFactor，多一步操作
     *   自动规范化 → 服务端按规则处理，用户不用操心
     * </pre>
     * 这里选自动规范化——**这类「派生字段」本来就该由服务端维护**，
     * 让用户去保证一致性是设计问题。
     */
    private void applyRequest(Exercise exercise, ExerciseSaveRequest request) {
        exercise.setName(request.getName().trim());
        exercise.setAlias(trimToNull(request.getAlias()));
        exercise.setPrimaryMuscle(request.getPrimaryMuscle());
        exercise.setSecondaryMuscles(trimToNull(request.getSecondaryMuscles()));
        exercise.setEquipment(request.getEquipment());
        exercise.setMovementPattern(request.getMovementPattern());
        exercise.setMetricType(request.getMetricType());
        exercise.setInstructions(trimToNull(request.getInstructions()));
        exercise.setCommonMistakes(trimToNull(request.getCommonMistakes()));
        exercise.setIsUnilateral(Boolean.TRUE.equals(request.getUnilateral()) ? 1 : 0);

        // ---------- 规范化 bwFactor ----------
        if (request.getMetricType() == MetricType.REPS_ONLY) {
            // 自重动作：没填就给个默认值 1.0（按整体重计算）。
            // 给默认值而不是报错——用户建一个「负重引体」时未必知道该填多少，
            // 1.0 是个合理的起点，之后可以改。
            exercise.setBwFactor(request.getBwFactor() == null
                    ? BigDecimal.ONE
                    : request.getBwFactor());
        } else {
            // 非自重动作：强制清空。
            // 留着值会让容量计算多算一遍体重——这是个静默的数据错误，
            // 不如直接由服务端抹掉。
            exercise.setBwFactor(null);
        }
    }

    /** 去空格；空字符串转成 null（便于数据库里统一用 NULL 表示「没填」） */
    private String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
        wrapper.eq(query.getPrimaryMuscle() != null, Exercise::getPrimaryMuscle, query.getPrimaryMuscle());
        wrapper.eq(query.getEquipment() != null, Exercise::getEquipment, query.getEquipment());
        wrapper.eq(query.getMovementPattern() != null, Exercise::getMovementPattern, query.getMovementPattern());
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
        //
        // ⚠️ **肌群必须按枚举顺序排，不能按字段值排。**
        // `ORDER BY primary_muscle` 走的是字符串序 → ARMS, BACK, CHEST, CORE,
        // LEGS, SHOULDERS, STRETCH, WARMUP。于是动作库第一屏全是「手臂」，
        // 而且「胸」排在「核心」后面——和任何人对身体部位的直觉都不一致。
        //
        // 用 FIELD() 指定顺序。这**必然**是枚举顺序在 SQL 里的第二份，
        // 所以有一条契约测试钉住两者一致（`ExerciseServiceTest`）——
        // 改枚举顺序而忘了改这里，测试会红。
        //
        // ⚠️ **整条 ORDER BY 必须一次性写在 `last()` 里。**
        // `last()` 永远是拼在 SQL 最末尾的，所以
        // `last("ORDER BY ...").orderByAsc(x)` 会生成
        // `ORDER BY ... ORDER BY x` —— 两个 ORDER BY，语法错误。
        //
        // 代价是列名要用手写的蛇形（`sort_order`），
        // 失去了 `Exercise::getSortOrder` 那种编译期检查。
        // 只有两列，且紧挨着写，可以接受。
        wrapper.last(muscleOrderClause());

        return wrapper;
    }

    /**
     * 按 {@link MuscleGroup} 的**声明顺序**排肌群。
     *
     * <p>直接写字段值出来，因为这是 MyBatis-Plus 的 wrapper 唯一能表达
     * 「自定义顺序」的方式（它没有 {@code orderByField}）。
     *
     * <p>{@code FIELD()} 对不在列表里的值返回 0，会排在最前——
     * 用户自建动作如果带了个没见过的肌群值，会冒到最上面。
     * 这不是坏事：那是**异常数据**，让它可见比让它沉底好。
     */
    private static String muscleOrderClause() {
        String values = java.util.Arrays.stream(MuscleGroup.values())
                .map(m -> "'" + m.name() + "'")
                .collect(java.util.stream.Collectors.joining(","));
        // 后两列和 FIELD 一起给出，因为整条 ORDER BY 只能出现在一个地方（见上方注释）
        return "ORDER BY FIELD(primary_muscle," + values
                + "), sort_order ASC, id ASC";
    }
}
