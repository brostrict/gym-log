package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.exercise.ExerciseService;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.program.dto.ProgramDetailResponse;
import com.gymlog.program.dto.ProgramSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 计划业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramMapper programMapper;
    private final WeekTemplateMapper weekTemplateMapper;
    private final DayTemplateMapper dayTemplateMapper;
    private final PrescribedExerciseMapper prescribedExerciseMapper;
    private final PrescribedSetMapper prescribedSetMapper;
    private final ExerciseMapper exerciseMapper;
    private final ExerciseService exerciseService;

    // ==================================================================
    // 创建
    // ==================================================================

    /**
     * 创建计划（含完整的嵌套结构）。
     *
     * <p><b>整个方法在一个事务里</b>——五层数据要么全部成功，要么全部回滚。
     *
     * <p>不这么做的话，中间失败会留下**半截计划**：
     * 有训练日但没有动作、有动作但没有组。
     * 这种数据在界面上表现为「计划打不开」或「训练日空白」，
     * 而且很难排查是哪里断的。
     *
     * <p>（Spring 的 {@code @Transactional} 默认只在
     * {@code RuntimeException} 时回滚。{@code BizException} 继承自
     * {@code RuntimeException}，所以校验失败也会正确回滚。）
     */
    @Transactional
    public Long create(Long userId, ProgramCreateRequest request) {
        // ---------- 0. 先做整体校验 ----------
        // 放在任何写库操作之前——尽早失败，避免白做一堆插入再回滚
        validateStructure(request);

        // ---------- 1. 计划 ----------
        Program program = new Program();
        program.setUserId(userId);
        program.setName(request.name().trim());
        program.setDescription(request.description());
        // totalWeeks 为 null 或 0 都表示不限期，统一存 0
        program.setTotalWeeks(request.totalWeeks() == null ? 0 : request.totalWeeks());
        program.setStartDate(request.startDate());
        program.setVersion(1);
        program.setStatus(ProgramStatus.ACTIVE);
        programMapper.insert(program);

        // rootId 指向自己——第一个版本是这条逻辑计划的根。
        // 需要插入后才能拿到自增 id，所以是「先插入再回填」，两次 SQL。
        program.setRootId(program.getId());
        programMapper.updateById(program);

        // ---------- 2. 周结构 ----------
        if (!CollectionUtils.isEmpty(request.weeks())) {
            for (ProgramCreateRequest.WeekRequest w : request.weeks()) {
                WeekTemplate week = new WeekTemplate();
                week.setProgramId(program.getId());
                week.setWeekNumber(w.weekNumber());
                week.setSessionsPerWeek(w.sessionsPerWeek() == null ? 3 : w.sessionsPerWeek());
                week.setWeightAdjustPct(w.weightAdjustPct());
                week.setSetAdjust(w.setAdjust() == null ? 0 : w.setAdjust());
                week.setIsDeload(Boolean.TRUE.equals(w.isDeload()) ? 1 : 0);
                week.setNote(w.note());
                weekTemplateMapper.insert(week);
            }
        }

        // ---------- 3. 训练日 → 动作 → 逐组 ----------
        if (!CollectionUtils.isEmpty(request.days())) {
            for (ProgramCreateRequest.DayRequest d : request.days()) {
                DayTemplate day = new DayTemplate();
                day.setProgramId(program.getId());
                day.setDayNumber(d.dayNumber());
                day.setName(d.name().trim());
                day.setIsRestDay(Boolean.TRUE.equals(d.isRestDay()) ? 1 : 0);
                day.setNote(d.note());
                dayTemplateMapper.insert(day);

                // 休息日不该有动作
                if (day.isRest() || CollectionUtils.isEmpty(d.exercises())) {
                    continue;
                }

                for (ProgramCreateRequest.PrescriptionRequest p : d.exercises()) {
                    // ⚠️ 校验动作可见性。
                    // 用户可能传入一个**别人的自定义动作 id**——
                    // 不校验的话，他的计划里就会出现别人私有的动作。
                    exerciseService.getVisibleById(userId, p.exerciseId());

                    PrescribedExercise pe = new PrescribedExercise();
                    pe.setDayTemplateId(day.getId());
                    pe.setExerciseId(p.exerciseId());
                    pe.setOrderIndex(p.orderIndex());
                    pe.setSupersetGroup(p.supersetGroup());
                    pe.setOrderInGroup(p.orderInGroup());
                    pe.setTargetSets(p.targetSets());
                    pe.setTargetRepsMin(p.targetRepsMin());
                    pe.setTargetRepsMax(p.targetRepsMax());
                    pe.setRestSec(p.restSec());
                    pe.setTargetWeightType(p.targetWeightType());
                    pe.setTargetWeight(p.targetWeight());
                    pe.setTargetWeightPct(p.targetWeightPct());
                    pe.setTargetRpe(p.targetRpe());
                    pe.setNote(p.note());
                    prescribedExerciseMapper.insert(pe);

                    if (!CollectionUtils.isEmpty(p.sets())) {
                        for (ProgramCreateRequest.SetRequest s : p.sets()) {
                            PrescribedSet set = new PrescribedSet();
                            set.setPrescribedExerciseId(pe.getId());
                            set.setSetNumber(s.setNumber());
                            set.setSetType(s.setType() == null
                                    ? com.gymlog.training.SetType.WORKING
                                    : s.setType());
                            set.setTargetReps(s.targetReps());
                            set.setTargetRepsMin(s.targetRepsMin());
                            set.setTargetRepsMax(s.targetRepsMax());
                            set.setTargetWeight(s.targetWeight());
                            set.setTargetWeightPct(s.targetWeightPct());
                            set.setTargetRpe(s.targetRpe());
                            set.setRestSec(s.restSec());
                            set.setNote(s.note());
                            prescribedSetMapper.insert(set);
                        }
                    }
                }
            }
        }

        log.info("创建计划 | userId={} | programId={} | name={} | 周数={} | 训练日={}",
                userId, program.getId(), program.getName(),
                request.weeks() == null ? 0 : request.weeks().size(),
                request.days() == null ? 0 : request.days().size());

        return program.getId();
    }

    // ==================================================================
    // 查询
    // ==================================================================

    /** 分页查询「我的计划」 */
    public IPage<ProgramSummaryResponse> list(Long userId, int page, int size, ProgramStatus status) {
        LambdaQueryWrapper<Program> wrapper = new LambdaQueryWrapper<Program>()
                .eq(Program::getUserId, userId)
                // 已归档的版本不在列表里显示——用户看到的是「当前有效的计划」
                .ne(Program::getStatus, ProgramStatus.ARCHIVED)
                .eq(status != null, Program::getStatus, status)
                .orderByDesc(Program::getCreatedAt)
                .orderByDesc(Program::getId);

        Page<Program> result = programMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                wrapper);

        // 统计每个计划的训练日数量。
        // 这里对每条记录查一次，在计划数量不大时（一个用户通常 < 10 个）可以接受。
        // 如果将来计划数量上去了，改成一次 GROUP BY 查询。
        return result.convert(p -> ProgramSummaryResponse.from(p, countTrainingDays(p.getId())));
    }

    /**
     * 查询计划详情（完整嵌套结构）。
     *
     * <p><b>固定 6 次查询，与计划规模无关</b>：
     * <pre>
     *   1. program
     *   2. week_template      WHERE program_id = ?
     *   3. day_template       WHERE program_id = ?
     *   4. prescribed_exercise WHERE day_template_id IN (...)
     *   5. prescribed_set      WHERE prescribed_exercise_id IN (...)
     *   6. exercise            WHERE id IN (...)          ← 取动作名称
     * </pre>
     *
     * <p><b>为什么不用嵌套查询（N+1）</b>：一个 8 周计划有 3 个训练日、
     * 约 20 个动作。逐层查询会是 1 + 3 + 20 + 60 = 84 次数据库往返。
     * 而 IN 查询不管多少条都只走一次网络。
     *
     * <p>这就是「**避免 N+1 查询**」的实践——
     * ORM 让「对象.集合.再集合」写起来很自然，
     * 但每次点号都可能是一次数据库查询。
     */
    public ProgramDetailResponse detail(Long userId, Long programId) {
        Program program = loadOwned(userId, programId);

        // 周
        List<WeekTemplate> weeks = weekTemplateMapper.selectList(
                new LambdaQueryWrapper<WeekTemplate>()
                        .eq(WeekTemplate::getProgramId, programId)
                        .orderByAsc(WeekTemplate::getWeekNumber));

        // 训练日
        List<DayTemplate> days = dayTemplateMapper.selectList(
                new LambdaQueryWrapper<DayTemplate>()
                        .eq(DayTemplate::getProgramId, programId)
                        .orderByAsc(DayTemplate::getDayNumber));

        if (days.isEmpty()) {
            return ProgramDetailResponse.assemble(program, weeks, List.of());
        }

        List<Long> dayIds = days.stream().map(DayTemplate::getId).toList();

        // 所有处方动作（一次 IN 查询覆盖所有训练日）
        List<PrescribedExercise> allPrescriptions = prescribedExerciseMapper.selectList(
                new LambdaQueryWrapper<PrescribedExercise>()
                        .in(PrescribedExercise::getDayTemplateId, dayIds)
                        .orderByAsc(PrescribedExercise::getOrderIndex));

        // 按训练日分组。
        // 排序规则：先按 superset_group（NULL 排最后），再按 order_in_group / order_index。
        // 这样超级组内的动作会连续排列，与前端的展示顺序一致。
        Map<Long, List<PrescribedExercise>> byDay = allPrescriptions.stream()
                .collect(Collectors.groupingBy(PrescribedExercise::getDayTemplateId));

        // 抽成独立方法，让这两个变量只被赋值一次。
        //
        // ⚠️ Java 要求 lambda 捕获的局部变量必须是 **effectively final**——
        // 声明后不再重新赋值。下面组装阶段的两个 lambda 都要用这两个 Map，
        // 如果在方法体内「先声明空 Map 再 if 里重新赋值」，编译会直接报错。
        //
        // 这不只是语法限制：**可变变量被 lambda 捕获本身就有风险**——
        // lambda 执行时机不确定，读到哪个版本的值不好推理。
        final Map<Long, List<PrescribedSet>> setsByExercise = loadSets(allPrescriptions);
        final Map<Long, Exercise> exercisesById = loadExercises(allPrescriptions);

        // ---------- 在内存里组装 ----------
        List<ProgramDetailResponse.DayItem> dayItems = new ArrayList<>(days.size());
        for (DayTemplate day : days) {
            List<PrescribedExercise> prescriptions =
                    byDay.getOrDefault(day.getId(), List.of());

            List<ProgramDetailResponse.PrescriptionItem> items = sortPrescriptions(prescriptions)
                    .stream()
                    .map(p -> {
                        Exercise e = exercisesById.get(p.getExerciseId());
                        List<ProgramDetailResponse.SetItem> sets =
                                setsByExercise.getOrDefault(p.getId(), List.of())
                                        .stream()
                                        .map(ProgramDetailResponse.SetItem::from)
                                        .toList();

                        return ProgramDetailResponse.prescriptionFrom(
                                p,
                                e == null ? null : e.getName(),
                                e == null || e.getPrimaryMuscle() == null ? null : e.getPrimaryMuscle().name(),
                                e == null || e.getPrimaryMuscle() == null ? null : e.getPrimaryMuscle().getDisplayName(),
                                e == null || e.getMetricType() == null ? null : e.getMetricType().name(),
                                sets);
                    })
                    .toList();

            dayItems.add(ProgramDetailResponse.dayFrom(day, items));
        }

        return ProgramDetailResponse.assemble(program, weeks, dayItems);
    }

    // ==================================================================
    // 更新与删除
    // ==================================================================

    /**
     * 更新计划的基本信息（名称、说明）。
     *
     * <p><b>⚠️ 这里刻意不支持改结构</b>（增删训练日、改动作）。
     *
     * <p>结构变更要走**版本化**路径（步骤 2.14）：
     * 归档旧版本 + 新建新版本。原因见 REQUIREMENTS 6.3 的不变量 2——
     * 直接改结构会让已完成的训练记录「追溯性地改变含义」。
     *
     * <p>本步骤先只支持元信息修改，结构编辑在 2.14 实现。
     */
    @Transactional
    public void updateMeta(Long userId, Long programId, String name, String description) {
        Program program = loadOwned(userId, programId);

        if (!program.isEditable()) {
            throw new BizException(ErrorCode.PROGRAM_ALREADY_STARTED,
                    "已归档或已完成的计划不能修改");
        }

        Program update = new Program();
        update.setId(programId);
        update.setName(name == null ? null : name.trim());
        update.setDescription(description);
        programMapper.updateById(update);

        log.info("更新计划信息 | userId={} | programId={}", userId, programId);
    }

    /**
     * 暂停 / 恢复计划。
     *
     * <p>暂停期间**不计入完成率的分母**——用户受伤休息两周，
     * 不该被算成「计划完成度只有 60%」。
     */
    @Transactional
    public void changeStatus(Long userId, Long programId, ProgramStatus target) {
        Program program = loadOwned(userId, programId);

        if (target == ProgramStatus.ARCHIVED) {
            throw new BizException(ErrorCode.FORBIDDEN, "归档由版本化流程触发，不能直接设置");
        }

        Program update = new Program();
        update.setId(programId);
        update.setStatus(target);
        programMapper.updateById(update);

        log.info("计划状态变更 | userId={} | programId={} | {} -> {}",
                userId, programId, program.getStatus(), target);
    }

    /**
     * 删除计划（逻辑删除，级联删除其下所有结构）。
     *
     * <p><b>⚠️ 待办</b>：步骤 2.14 完成后要加「有训练记录引用时不能删」的检查。
     * 现在删掉计划后，历史训练记录会指向一个已删除的计划。
     */
    @Transactional
    public void delete(Long userId, Long programId) {
        Program program = loadOwned(userId, programId);

        // 级联删除（这里是物理删除子表，主表走逻辑删除）
        //
        // 为什么不给子表也加 deleted 字段：
        // 子表数据脱离主表没有意义（没有计划，训练日就是孤儿数据），
        // 逻辑删除只会让它们永远留在库里占空间。
        cascadeDelete(programId);

        programMapper.deleteById(programId);

        log.info("删除计划 | userId={} | programId={} | name={}",
                userId, programId, program.getName());
    }

    // ==================================================================
    // 内部方法
    // ==================================================================

    /**
     * 加载属于该用户的计划。
     *
     * <p>不满足条件统一抛 404（而不是 403）——理由同动作库：
     * 不泄露「这个 id 存在」。
     */
    private Program loadOwned(Long userId, Long programId) {
        Program program = programMapper.selectById(programId);
        if (program == null || !program.isOwnedBy(userId)) {
            throw new BizException(ErrorCode.PROGRAM_NOT_FOUND);
        }
        return program;
    }

    /**
     * 结构校验。
     *
     * <p><b>为什么这些校验不能靠注解做</b>：
     * 它们都是**跨记录**的约束——周序号不能重复、超级组必须成对出现。
     * JSR-303 注解作用在单个字段上，表达不了「这个列表里的值必须互不相同」。
     */
    private void validateStructure(ProgramCreateRequest request) {
        // ---------- 周序号不重复 ----------
        if (!CollectionUtils.isEmpty(request.weeks())) {
            long distinct = request.weeks().stream()
                    .map(ProgramCreateRequest.WeekRequest::weekNumber)
                    .distinct().count();
            if (distinct != request.weeks().size()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "周序号不能重复");
            }
        }

        if (CollectionUtils.isEmpty(request.days())) {
            return;
        }

        // ---------- 训练日序号不重复 ----------
        long distinctDays = request.days().stream()
                .map(ProgramCreateRequest.DayRequest::dayNumber)
                .distinct().count();
        if (distinctDays != request.days().size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "训练日序号不能重复");
        }

        // ---------- 每个训练日内的约束 ----------
        for (ProgramCreateRequest.DayRequest day : request.days()) {
            if (Boolean.TRUE.equals(day.isRestDay())) {
                if (!CollectionUtils.isEmpty(day.exercises())) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "休息日不能包含动作：" + day.name());
                }
                continue;
            }

            if (CollectionUtils.isEmpty(day.exercises())) {
                continue;
            }

            validateExercises(day);

            // ---------- 逐组处方的组序号不重复 ----------
            for (ProgramCreateRequest.PrescriptionRequest p : day.exercises()) {
                if (CollectionUtils.isEmpty(p.sets())) {
                    continue;
                }
                long distinctSets = p.sets().stream()
                        .map(ProgramCreateRequest.SetRequest::setNumber)
                        .distinct().count();
                if (distinctSets != p.sets().size()) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "同一动作的组序号不能重复：" + day.name());
                }
            }
        }
    }

    /**
     * 校验训练日内的动作编排。
     *
     * <p>重点是**超级组的完整性**——这是最容易配错的地方。
     */
    private void validateExercises(ProgramCreateRequest.DayRequest day) {
        List<ProgramCreateRequest.PrescriptionRequest> exercises = day.exercises();

        // 同一个训练日内，orderIndex 不能重复
        long distinctOrder = exercises.stream()
                .map(ProgramCreateRequest.PrescriptionRequest::orderIndex)
                .distinct().count();
        if (distinctOrder != exercises.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "同一训练日内动作顺序不能重复：" + day.name());
        }

        // 按超级组编号分组
        Map<Integer, List<ProgramCreateRequest.PrescriptionRequest>> groups = exercises.stream()
                .filter(p -> p.supersetGroup() != null)
                .collect(Collectors.groupingBy(
                        ProgramCreateRequest.PrescriptionRequest::supersetGroup));

        for (Map.Entry<Integer, List<ProgramCreateRequest.PrescriptionRequest>> entry : groups.entrySet()) {
            List<ProgramCreateRequest.PrescriptionRequest> members = entry.getValue();

            // ① 超级组至少要有 2 个动作——只有 1 个就不是超级组
            if (members.size() < 2) {
                throw new BizException(ErrorCode.SUPERSET_GROUP_INVALID,
                        "超级组 " + entry.getKey() + " 至少需要 2 个动作");
            }

            // ② 组内每个动作必须有 orderInGroup
            if (members.stream().anyMatch(m -> m.orderInGroup() == null)) {
                throw new BizException(ErrorCode.SUPERSET_GROUP_INVALID,
                        "超级组 " + entry.getKey() + " 内的动作必须指定组内顺序");
            }

            // ③ 组内顺序不能重复。
            //    重复的话，前端不知道该先做哪个，执行顺序就不确定了。
            long distinctInGroup = members.stream()
                    .map(ProgramCreateRequest.PrescriptionRequest::orderInGroup)
                    .distinct().count();
            if (distinctInGroup != members.size()) {
                throw new BizException(ErrorCode.SUPERSET_GROUP_INVALID,
                        "超级组 " + entry.getKey() + " 内的顺序不能重复");
            }
        }

        // ④ 不在超级组里的动作不该有 orderInGroup——
        //    有的话说明客户端传参不一致，容易让人误解
        boolean stray = exercises.stream()
                .anyMatch(p -> p.supersetGroup() == null && p.orderInGroup() != null);
        if (stray) {
            throw new BizException(ErrorCode.SUPERSET_GROUP_INVALID,
                    "非超级组动作不应指定组内顺序");
        }
    }

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
    private List<PrescribedExercise> sortPrescriptions(List<PrescribedExercise> prescriptions) {
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

    /**
     * 一次查出所有处方动作的逐组配置，按动作 id 分组。
     *
     * <p>空列表时直接返回空 Map——**不查库**。
     * 不判空的话会生成 {@code WHERE id IN ()} 这种无意义的 SQL，
     * 某些数据库会直接报语法错误。
     */
    private Map<Long, List<PrescribedSet>> loadSets(List<PrescribedExercise> prescriptions) {
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
    private Map<Long, Exercise> loadExercises(List<PrescribedExercise> prescriptions) {
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

    /** 统计某计划下的训练日数量（不含休息日） */
    private int countTrainingDays(Long programId) {
        Long count = dayTemplateMapper.selectCount(
                new LambdaQueryWrapper<DayTemplate>()
                        .eq(DayTemplate::getProgramId, programId)
                        .eq(DayTemplate::getIsRestDay, 0));
        return count == null ? 0 : count.intValue();
    }

    /** 级联删除计划下的所有结构 */
    private void cascadeDelete(Long programId) {
        List<DayTemplate> days = dayTemplateMapper.selectList(
                new LambdaQueryWrapper<DayTemplate>().eq(DayTemplate::getProgramId, programId));

        if (!days.isEmpty()) {
            List<Long> dayIds = days.stream().map(DayTemplate::getId).toList();

            List<PrescribedExercise> prescriptions = prescribedExerciseMapper.selectList(
                    new LambdaQueryWrapper<PrescribedExercise>()
                            .in(PrescribedExercise::getDayTemplateId, dayIds));

            if (!prescriptions.isEmpty()) {
                List<Long> peIds = prescriptions.stream().map(PrescribedExercise::getId).toList();
                prescribedSetMapper.delete(
                        new LambdaQueryWrapper<PrescribedSet>()
                                .in(PrescribedSet::getPrescribedExerciseId, peIds));
                prescribedExerciseMapper.delete(
                        new LambdaQueryWrapper<PrescribedExercise>()
                                .in(PrescribedExercise::getDayTemplateId, dayIds));
            }

            dayTemplateMapper.delete(
                    new LambdaQueryWrapper<DayTemplate>().eq(DayTemplate::getProgramId, programId));
        }

        weekTemplateMapper.delete(
                new LambdaQueryWrapper<WeekTemplate>().eq(WeekTemplate::getProgramId, programId));
    }
}
