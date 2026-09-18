package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.exercise.ExerciseService;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.program.dto.ProgramDetailResponse;
import com.gymlog.program.dto.ProgramFromTemplateRequest;
import com.gymlog.program.dto.ProgramStructureRequest;
import com.gymlog.program.dto.ProgramSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ProgramTemplateMapper templateMapper;
    /** 解析模板的 JSON 结构。复用 Spring 容器里那一个，不用自己 new */
    private final ObjectMapper objectMapper;
    /** 归属校验、排序规则、批量加载 —— 与 ExpansionService 共用同一份实现 */
    private final ProgramStructureSupport structureSupport;

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
        validateStructure(request.weeks(), request.days());

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

        // ---------- 2. 周结构 + 训练日 ----------
        insertStructure(userId, program.getId(), request.weeks(), request.days());

        log.info("创建计划 | userId={} | programId={} | name={} | 周数={} | 训练日={}",
                userId, program.getId(), program.getName(),
                request.weeks() == null ? 0 : request.weeks().size(),
                request.days() == null ? 0 : request.days().size());

        return program.getId();
    }

    /**
     * 把周与训练日写进库里 —— **创建和结构编辑共用这一份**。
     *
     * <p>抽出来的理由很直接：这两条路径要写的记录完全一样
     * （周 → 训练日 → 动作 → 逐组），只有「之前有没有旧数据」不同。
     * 复制一份的话，将来加一个字段（比如动作的「组间休息提示音」）
     * 就得记住改两个地方，而**漏改一边不会有任何报错**，
     * 只会让「创建的计划」和「编辑过的计划」行为不一致。
     *
     * <p>⚠️ 调用方负责：① 已校验过结构；② 已清空旧结构（编辑路径）。
     */
    private void insertStructure(Long userId,
                                 Long programId,
                                 List<ProgramCreateRequest.WeekRequest> weeks,
                                 List<ProgramCreateRequest.DayRequest> days) {

        // ---------- 周结构 ----------
        if (!CollectionUtils.isEmpty(weeks)) {
            for (ProgramCreateRequest.WeekRequest w : weeks) {
                WeekTemplate week = new WeekTemplate();
                week.setProgramId(programId);
                week.setWeekNumber(w.weekNumber());
                week.setSessionsPerWeek(w.sessionsPerWeek() == null ? 3 : w.sessionsPerWeek());
                week.setWeightAdjustPct(w.weightAdjustPct());
                week.setSetAdjust(w.setAdjust() == null ? 0 : w.setAdjust());
                week.setIsDeload(Boolean.TRUE.equals(w.isDeload()) ? 1 : 0);
                week.setNote(w.note());
                weekTemplateMapper.insert(week);
            }
        }

        // ---------- 训练日 → 动作 → 逐组 ----------
        if (CollectionUtils.isEmpty(days)) {
            return;
        }

        for (ProgramCreateRequest.DayRequest d : days) {
            DayTemplate day = new DayTemplate();
            day.setProgramId(programId);
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
                //
                // 编辑路径同样要校验：老计划里可能引用着一个
                // 后来被删除/停用的自定义动作，直接原样写回就等于绕过了校验。
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
                pe.setTargetDurationSec(p.targetDurationSec());
                pe.setAnnounceIntervalSec(p.announceIntervalSec());
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

    /**
     * 编辑计划结构 —— **全量替换**。
     *
     * <h3>流程</h3>
     * <pre>
     *   1. 归属校验（不通过 → 404，不泄露存在性）
     *   2. 状态守卫（已归档的计划不能改）
     *   3. 乐观锁（版本号对不上 → 409）
     *   4. 结构校验（复用创建时那套跨记录规则）
     *   5. 删掉全部旧结构 → 写入新结构
     *   6. 版本号 +1
     * </pre>
     *
     * <h3>为什么敢直接删了重建</h3>
     *
     * <p>因为**没有任何东西外键引用这些子记录**。
     * 会话快照存的是处方**值**（重量、次数、休息），不是
     * {@code prescribed_exercise_id}——这是 REQUIREMENTS 6.3 不变量 2
     * 明确的实现约束。
     *
     * <p>如果哪天会话改成引用结构表，这个方法必须推倒重来，
     * 改成基于 id 的增量更新。**这个前提要一直记着。**
     *
     * <h3>第 3 步的乐观锁不是可选项</h3>
     *
     * <p>全量替换下，两个设备同时编辑会**静默丢数据**：
     * <pre>
     *   手机加了深蹲 → 保存
     *   电脑加了卧推 → 保存      ← 深蹲被整份覆盖，两边都没提示
     * </pre>
     * 带上 {@code expectedVersion} 后，第二个保存会拿到 409，
     * 客户端刷新重来。
     *
     * <p>（用 {@code Program.version} 而不是新建一张锁表：
     * 这个字段本来就在，之前一直没用上。乐观锁只需要一个单调递增的计数器。）
     */
    @Transactional
    public ProgramDetailResponse updateStructure(Long userId,
                                                 Long programId,
                                                 ProgramStructureRequest request) {
        Program program = structureSupport.loadOwned(userId, programId);

        // ---------- 状态守卫 ----------
        if (!program.isEditable()) {
            throw new BizException(ErrorCode.PROGRAM_NOT_EDITABLE);
        }

        // ---------- 乐观锁 ----------
        // 版本号为 null 的老数据按 1 处理，避免历史数据永远改不了
        int currentVersion = program.getVersion() == null ? 1 : program.getVersion();
        if (!Integer.valueOf(currentVersion).equals(request.expectedVersion())) {
            throw new BizException(ErrorCode.PROGRAM_VERSION_CONFLICT,
                    String.format("计划已被其他设备修改（当前版本 %d，你提交的是 %d），请刷新后重试",
                            currentVersion, request.expectedVersion()));
        }

        // ---------- 结构校验 ----------
        validateStructure(request.weeks(), request.days());

        // ---------- 替换 ----------
        // 先删后插，同一个事务。中间状态对外不可见。
        cascadeDelete(programId);
        insertStructure(userId, programId, request.weeks(), request.days());

        // ---------- 版本 +1 ----------
        Program update = new Program();
        update.setId(programId);
        update.setVersion(currentVersion + 1);
        programMapper.updateById(update);

        log.info("编辑计划结构 | userId={} | programId={} | {} -> {} | 周数={} | 训练日={}",
                userId, programId, currentVersion, currentVersion + 1,
                request.weeks() == null ? 0 : request.weeks().size(),
                request.days() == null ? 0 : request.days().size());

        // 返回完整的新结构：全量替换后所有子记录的 id 都变了，
        // 客户端手里那份缓存已经失效，必须拿到新的。
        // 顺带把新版本号带回去，用户可以接着编辑不用重新 GET。
        return detail(userId, programId);
    }

    /**
     * 从内置模板创建计划。
     *
     * <p><b>流程</b>：
     * <pre>
     *   1. 按 code 加载模板（必须是上架状态）
     *   2. 解析 structure JSON
     *   3. 把 JSON 里的「动作名称」解析成「动作 id」   ← 关键步骤
     *   4. 组装成 ProgramCreateRequest
     *   5. 调用 create()——复用已有的嵌套创建逻辑
     * </pre>
     *
     * <p><b>第 5 步复用 create 而不是重写一遍</b>：
     * 校验、事务、级联插入的逻辑完全一样。
     * 复制一份的话，将来改 create 就忘不了同步改这里。
     *
     * <p><b>第 3 步是唯一的额外工作</b>：模板里存名称是因为动作 id
     * 各环境不一致，但入库需要 id。这层转换必须有，
     * 而且要**明确报错**——名称对不上时告诉用户是哪个动作，
     * 而不是抛一个笼统的「创建失败」。
     */
    @Transactional
    public Long createFromTemplate(Long userId, ProgramFromTemplateRequest request) {
        ProgramTemplate template = templateMapper.selectOne(
                new LambdaQueryWrapper<ProgramTemplate>()
                        .eq(ProgramTemplate::getCode, request.templateCode())
                        .eq(ProgramTemplate::getStatus, ProgramTemplate.STATUS_PUBLISHED));

        if (template == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "计划模板不存在或已下架");
        }

        ProgramCreateRequest createRequest = buildRequestFromTemplate(
                template,
                request.name() == null || request.name().isBlank() ? template.getName() : request.name().trim(),
                request.startDate());

        Long programId = create(userId, createRequest);

        // 记录来源模板。便于统计「哪个模板最受欢迎」，
        // 也便于将来做「以模板最新版重新开始」。
        Program update = new Program();
        update.setId(programId);
        update.setTemplateCode(template.getCode());
        programMapper.updateById(update);

        log.info("从模板创建计划 | userId={} | programId={} | template={}",
                userId, programId, template.getCode());

        return programId;
    }

    /**
     * 把模板的 JSON 结构转成创建请求，同时解析动作名称。
     *
     * <p><b>名称解析做了缓存</b>：同一个动作在模板里可能出现多次
     * （比如深蹲在 A 日和 B 日都有），查一次库就够。
     */
    private ProgramCreateRequest buildRequestFromTemplate(ProgramTemplate template,
                                                          String planName,
                                                          java.time.LocalDate startDate) {
        JsonNode root;
        try {
            root = objectMapper.readTree(template.getStructure());
        } catch (JsonProcessingException e) {
            // 模板是种子数据，解析失败说明数据有问题，不是用户输入错误。
            // 记 error 日志便于运维定位，但对用户只能给笼统提示。
            log.error("模板结构 JSON 解析失败 | template={}", template.getCode(), e);
            throw new BizException(ErrorCode.SYSTEM_ERROR, "模板数据异常，请联系管理员");
        }

        // ---------- 解析所有用到的动作名称 → id ----------
        Map<String, Long> exerciseIdByName = resolveExerciseIds(root, template.getCode());

        // ---------- 组装请求 ----------
        List<ProgramCreateRequest.WeekRequest> weeks = new ArrayList<>();
        for (JsonNode w : root.path("weeks")) {
            weeks.add(new ProgramCreateRequest.WeekRequest(
                    w.path("weekNumber").asInt(),
                    w.hasNonNull("sessionsPerWeek") ? w.get("sessionsPerWeek").asInt() : null,
                    w.hasNonNull("weightAdjustPct") ? w.get("weightAdjustPct").decimalValue() : null,
                    w.hasNonNull("setAdjust") ? w.get("setAdjust").asInt() : null,
                    w.hasNonNull("isDeload") && w.get("isDeload").asBoolean(),
                    w.hasNonNull("note") ? w.get("note").asText() : null
            ));
        }

        List<ProgramCreateRequest.DayRequest> days = new ArrayList<>();
        for (JsonNode d : root.path("days")) {
            List<ProgramCreateRequest.PrescriptionRequest> exercises = new ArrayList<>();

            for (JsonNode e : d.path("exercises")) {
                String exerciseName = e.path("exerciseName").asText();
                Long exerciseId = exerciseIdByName.get(exerciseName);
                if (exerciseId == null) {
                    // 理论上不会走到——ProgramTemplateTest 已经保证名称都存在。
                    // 但如果管理员在运行期改了动作名称，就会到这里。
                    throw new BizException(ErrorCode.SYSTEM_ERROR,
                            "模板引用的动作「" + exerciseName + "」不存在，请联系管理员");
                }
                exercises.add(buildPrescription(e, exerciseId));
            }

            days.add(new ProgramCreateRequest.DayRequest(
                    d.path("dayNumber").asInt(),
                    d.path("name").asText(),
                    d.hasNonNull("isRestDay") && d.get("isRestDay").asBoolean(),
                    d.hasNonNull("note") ? d.get("note").asText() : null,
                    exercises
            ));
        }

        return new ProgramCreateRequest(
                planName,
                template.getDescription(),
                template.getTotalWeeks(),
                startDate,
                weeks,
                days
        );
    }

    /** 从模板 JSON 里收集所有动作名称，一次查出对应的 id */
    private Map<String, Long> resolveExerciseIds(JsonNode root, String templateCode) {
        // 用 LinkedHashSet 保序，便于出错时输出的顺序稳定
        Set<String> names = new java.util.LinkedHashSet<>();
        for (JsonNode day : root.path("days")) {
            for (JsonNode ex : day.path("exercises")) {
                if (ex.hasNonNull("exerciseName")) {
                    names.add(ex.get("exerciseName").asText());
                }
            }
        }

        if (names.isEmpty()) {
            return Map.of();
        }

        // 一次 IN 查询解决所有名称，而不是逐个查。
        // 一个模板引用 10-20 个不重复的动作，逐个查就是 10-20 次往返。
        List<Exercise> exercises = exerciseMapper.selectList(
                new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID)
                        .in(Exercise::getName, names));

        Map<String, Long> result = new java.util.HashMap<>();
        for (Exercise e : exercises) {
            result.put(e.getName(), e.getId());
        }

        // 报告缺失的动作。一次性列出全部而不是遇到第一个就抛——
        // 便于运维一次修完。
        List<String> missing = names.stream().filter(n -> !result.containsKey(n)).toList();
        if (!missing.isEmpty()) {
            log.error("模板引用的动作不存在 | template={} | missing={}", templateCode, missing);
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "模板数据不完整，缺少动作：" + String.join("、", missing));
        }

        return result;
    }

    /** 把一个模板里的动作条目转成创建请求 */
    private ProgramCreateRequest.PrescriptionRequest buildPrescription(JsonNode e, Long exerciseId) {
        List<ProgramCreateRequest.SetRequest> sets = new ArrayList<>();
        for (JsonNode s : e.path("sets")) {
            sets.add(new ProgramCreateRequest.SetRequest(
                    s.path("setNumber").asInt(),
                    s.hasNonNull("setType")
                            ? com.gymlog.training.SetType.valueOf(s.get("setType").asText())
                            : null,
                    intOrNull(s, "targetReps"),
                    intOrNull(s, "targetRepsMin"),
                    intOrNull(s, "targetRepsMax"),
                    decimalOrNull(s, "targetWeight"),
                    decimalOrNull(s, "targetWeightPct"),
                    decimalOrNull(s, "targetRpe"),
                    intOrNull(s, "restSec"),
                    s.hasNonNull("note") ? s.get("note").asText() : null
            ));
        }

        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId,
                e.path("orderIndex").asInt(),
                intOrNull(e, "supersetGroup"),
                intOrNull(e, "orderInGroup"),
                e.path("targetSets").asInt(),
                intOrNull(e, "targetRepsMin"),
                intOrNull(e, "targetRepsMax"),
                e.hasNonNull("restSec") ? e.get("restSec").asInt() : 90,
                e.hasNonNull("targetWeightType")
                        ? TargetWeightType.valueOf(e.get("targetWeightType").asText())
                        : TargetWeightType.ABSOLUTE,
                decimalOrNull(e, "targetWeight"),
                decimalOrNull(e, "targetWeightPct"),
                decimalOrNull(e, "targetRpe"),
                // 时长类动作的目标（V14 起是正式字段）
                intOrNull(e, "targetDurationSec"),
                intOrNull(e, "announceIntervalSec"),
                e.hasNonNull("note") ? e.get("note").asText() : null,
                sets
        );
    }

    private Integer intOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asInt() : null;
    }

    private java.math.BigDecimal decimalOrNull(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).decimalValue() : null;
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
        Program program = structureSupport.loadOwned(userId, programId);

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
        final Map<Long, List<PrescribedSet>> setsByExercise = structureSupport.loadSets(allPrescriptions);
        final Map<Long, Exercise> exercisesById = structureSupport.loadExercises(allPrescriptions);

        // ---------- 在内存里组装 ----------
        List<ProgramDetailResponse.DayItem> dayItems = new ArrayList<>(days.size());
        for (DayTemplate day : days) {
            List<PrescribedExercise> prescriptions =
                    byDay.getOrDefault(day.getId(), List.of());

            List<ProgramDetailResponse.PrescriptionItem> items = structureSupport.sortPrescriptions(prescriptions)
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
     * <p><b>只改元信息，不动结构。</b>结构编辑走
     * {@link #updateStructure}（`PUT /programs/{id}/structure`）。
     *
     * <p><b>为什么拆成两个接口而不是合成一个</b>：
     * 全量替换结构要删掉上百条子记录再重建，还会让版本号 +1。
     * 改个名字不该有这些副作用——改名是低频小操作，
     * 不该触发整个计划的 id 洗牌，也不该让另一台设备上
     * 正在编辑的计划突然变成冲突状态。
     *
     * <p>这个接口**不动版本号**：版本号只在结构变化时递增，
     * 它标记的是「结构变了几次」，不是「记录改了几次」。
     */
    @Transactional
    public void updateMeta(Long userId, Long programId, String name, String description) {
        Program program = structureSupport.loadOwned(userId, programId);

        if (!program.isEditable()) {
            throw new BizException(ErrorCode.PROGRAM_NOT_EDITABLE);
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
        Program program = structureSupport.loadOwned(userId, programId);

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
     * <p><b>⚠️ 待办（Phase 3 补）</b>：要加「有训练记录引用时不能删」的检查。
     * 现在删掉计划后，历史训练记录会指向一个已删除的计划。
     * 检查本身很简单（数一下 session 表），但 session 表要到 Phase 3 才建，
     * 现在写了也没法测——**没法测的代码等于没有代码**，所以留到那时一起做。
     */
    @Transactional
    public void delete(Long userId, Long programId) {
        Program program = structureSupport.loadOwned(userId, programId);

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
     * 结构校验。
     *
     * <p><b>为什么这些校验不能靠注解做</b>：
     * 它们都是**跨记录**的约束——周序号不能重复、超级组必须成对出现。
     * JSR-303 注解作用在单个字段上，表达不了「这个列表里的值必须互不相同」。
     */
    private void validateStructure(List<ProgramCreateRequest.WeekRequest> weeks,
                                   List<ProgramCreateRequest.DayRequest> days) {
        // ---------- 周序号不重复 ----------
        if (!CollectionUtils.isEmpty(weeks)) {
            long distinct = weeks.stream()
                    .map(ProgramCreateRequest.WeekRequest::weekNumber)
                    .distinct().count();
            if (distinct != weeks.size()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "周序号不能重复");
            }
        }

        if (CollectionUtils.isEmpty(days)) {
            return;
        }

        // ---------- 训练日序号不重复 ----------
        long distinctDays = days.stream()
                .map(ProgramCreateRequest.DayRequest::dayNumber)
                .distinct().count();
        if (distinctDays != days.size()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "训练日序号不能重复");
        }

        // ---------- 每个训练日内的约束 ----------
        for (ProgramCreateRequest.DayRequest day : days) {
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
