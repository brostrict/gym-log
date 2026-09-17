package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 计划结构的读取与排序 —— 被 {@code ProgramService} 和 {@code ExpansionService} 共用。
 *
 * <h3>为什么单独抽一个类，而不是让两个 Service 互相调用</h3>
 *
 * <p>下面这三件事是**同一个领域规则**的两个使用场景：
 * <pre>
 *   ProgramService   → 展示计划结构（用户看「我的计划长什么样」）
 *   ExpansionService → 展开训练内容（用户看「今天具体练什么」）
 * </pre>
 *
 * <p>两者都需要「排序规则」和「批量加载」，但**都不拥有它**。
 * 让 {@code ExpansionService} 去调 {@code ProgramService} 的私有方法，
 * 会把两个本应平级的服务变成上下级依赖；
 * 复制一份则更糟——排序规则一旦分叉，
 * 「计划详情里的动作顺序」和「跟练时的动作顺序」就会不一致，
 * 而这种 bug 极难被发现（两边单独看都对）。
 *
 * <p><b>⚠️ 这里的排序规则必须只有一份实现。</b>
 */
@Component
@RequiredArgsConstructor
public class ProgramStructureSupport {

    private final ProgramMapper programMapper;
    private final PrescribedSetMapper prescribedSetMapper;
    private final ExerciseMapper exerciseMapper;

    // ==================================================================
    // 归属
    // ==================================================================

    /**
     * 加载属于该用户的计划。
     *
     * <p>不满足条件统一抛 404（而不是 403）——理由同动作库：
     * 不泄露「这个 id 存在」。
     */
    public Program loadOwned(Long userId, Long programId) {
        Program program = programMapper.selectById(programId);
        if (program == null || !program.isOwnedBy(userId)) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND);
        }
        return program;
    }

    // ==================================================================
    // 排序
    // ==================================================================

    /**
     * 处方动作的排序规则。
     *
     * <p><b>排序键的定义</b>：
     * <pre>
     *   普通动作   → 自己的 orderIndex
     *   超级组成员 → 组内**最小的** orderIndex
     * </pre>
     * 组内再按 {@code orderInGroup} 排。
     *
     * <p><b>⚠️ 这里踩过一个坑</b>：最初的实现是「有超级组的排在前面」，
     * 结果破坏了对 {@code orderIndex} 的尊重——
     *
     * <pre>
     *   用户设置：1.卧推(普通)  2.划船(超级组)  3.深蹲(超级组)  4.硬拉(普通)
     *   错误排序：划船, 深蹲, 卧推, 硬拉        ← 超级组被提到了最前面
     *   正确排序：卧推, 划船, 深蹲, 硬拉
     * </pre>
     *
     * <p>根源是把「分组」和「排序」混为一谈：超级组影响的是
     * **执行节奏**（组内不休息），不是**在训练日里的位置**。
     * 位置仍然由 orderIndex 决定。
     *
     * <p><b>为什么排序放在服务端而不是让前端做</b>：
     * 两端（Flutter App 和 Vue Web）都要展示同样的顺序。
     * 规则放服务端，只实现一次；放前端就要写两遍，且容易不一致。
     */
    public List<PrescribedExercise> sortPrescriptions(List<PrescribedExercise> prescriptions) {
        // 先算出每个超级组的排序键：组内最小的 orderIndex
        Map<Integer, Integer> groupSortKey = new HashMap<>();
        for (PrescribedExercise p : prescriptions) {
            if (p.isInSuperset()) {
                groupSortKey.merge(p.getSupersetGroup(), p.getOrderIndex(), Math::min);
            }
        }

        return prescriptions.stream()
                .sorted(Comparator
                        // 主键：普通动作用自己的 orderIndex，超级组用组内最小值
                        .comparingInt((PrescribedExercise p) -> p.isInSuperset()
                                ? groupSortKey.getOrDefault(p.getSupersetGroup(), Integer.MAX_VALUE)
                                : p.getOrderIndex())
                        // 次键：超级组内按组内顺序；普通动作为 0，不受影响
                        .thenComparingInt(p -> p.getOrderInGroup() == null ? 0 : p.getOrderInGroup())
                        // 末键：id 保证顺序稳定（相同键时不会在分页/多次请求间跳动）
                        .thenComparing(PrescribedExercise::getId))
                .toList();
    }

    // ==================================================================
    // 批量加载
    // ==================================================================

    /**
     * 一次查出所有处方动作的逐组配置，按动作 id 分组。
     *
     * <p>空列表时直接返回空 Map——**不查库**。
     * 不判空的话会生成 {@code WHERE id IN ()} 这种无意义的 SQL，
     * 某些数据库会直接报语法错误。
     */
    public Map<Long, List<PrescribedSet>> loadSets(List<PrescribedExercise> prescriptions) {
        if (prescriptions.isEmpty()) {
            return Map.of();
        }
        List<Long> peIds = prescriptions.stream().map(PrescribedExercise::getId).toList();
        return prescribedSetMapper.selectList(
                        new LambdaQueryWrapper<PrescribedSet>()
                                .in(PrescribedSet::getPrescribedExerciseId, peIds)
                                .orderByAsc(PrescribedSet::getSetNumber))
                .stream()
                .collect(Collectors.groupingBy(PrescribedSet::getPrescribedExerciseId));
    }

    /**
     * 一次查出所有涉及的动作（取名称、肌群用于展示）。
     *
     * <p>用 {@code selectList + in} 而不是已废弃的 {@code selectBatchIds}。
     */
    public Map<Long, Exercise> loadExercises(List<PrescribedExercise> prescriptions) {
        if (prescriptions.isEmpty()) {
            return Map.of();
        }
        List<Long> exerciseIds = prescriptions.stream()
                .map(PrescribedExercise::getExerciseId)
                .distinct()
                .toList();
        return exerciseMapper.selectList(
                        new LambdaQueryWrapper<Exercise>().in(Exercise::getId, exerciseIds))
                .stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));
    }
}
