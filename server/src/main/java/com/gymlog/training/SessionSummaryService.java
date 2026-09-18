package com.gymlog.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gymlog.exercise.MetricType;
import com.gymlog.training.dto.SessionListItem;
import com.gymlog.training.dto.SessionSummaryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 训练历史列表与训练总结。
 *
 * <h3>统计口径只有一份实现</h3>
 *
 * <p>所有数值都来自 {@link TrainingMetrics}（纯函数）。
 * 这里只负责查数据、累加、拼装——**不做任何口径判断**。
 *
 * <p>唯一的例外是 {@link SetRecordMapper#bestE1rmBefore}：
 * 「扫描全部历史取最佳 e1RM」在 SQL 里是一次聚合，在 Java 里要把
 * 用户所有历史拉进内存。那份重复由契约测试守住。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionSummaryService {

    private final SessionService sessionService;
    private final WorkoutSessionMapper sessionMapper;
    private final SessionExerciseMapper sessionExerciseMapper;
    private final SetRecordMapper setRecordMapper;
    private final SessionSetTargetMapper sessionSetTargetMapper;

    // ==================================================================
    // 历史列表
    // ==================================================================

    /**
     * 训练历史（分页，按时间倒序）。
     *
     * <p><b>统计值在 Java 里算，不用 SQL 聚合</b>：容量口径依赖
     * {@code metric_type} + {@code bw_factor} + 快照体重三个字段的交叉判断，
     * 写成 SQL 是一大段 CASE WHEN，而且会和 {@link TrainingMetrics} 分叉。
     *
     * <p>代价是每页要把这些会话的组记录读出来。一页 20 场 × 约 30 组
     * = 600 行，每条记录只有几个数字——比口径分叉便宜得多。
     */
    public IPage<SessionListItem> history(Long userId, int page, int size) {
        Page<WorkoutSession> result = sessionMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<WorkoutSession>()
                        .eq(WorkoutSession::getUserId, userId)
                        .orderByDesc(WorkoutSession::getStartedAt)
                        .orderByDesc(WorkoutSession::getId));

        if (result.getRecords().isEmpty()) {
            return result.convert(s -> null);
        }

        List<Long> sessionIds = result.getRecords().stream().map(WorkoutSession::getId).toList();
        StatsBundle bundle = loadStats(sessionIds);

        return result.convert(s -> toListItem(s, bundle));
    }

    // ==================================================================
    // 训练总结
    // ==================================================================

    /**
     * 一次训练的总结 —— 练完之后那一屏。
     */
    public SessionSummaryResponse summary(Long userId, Long sessionId) {
        WorkoutSession session = sessionService.loadOwned(userId, sessionId);

        List<SessionExercise> exercises = sessionExerciseMapper.selectList(
                new LambdaQueryWrapper<SessionExercise>()
                        .eq(SessionExercise::getSessionId, sessionId)
                        .orderByAsc(SessionExercise::getOrderIndex));

        Map<Long, List<SetRecord>> recordsByExercise = loadRecords(
                exercises.stream().map(SessionExercise::getId).toList());
        Map<Long, List<SessionSetTarget>> targetsByExercise = loadTargets(exercises);

        // ---------- 逐动作明细 ----------
        List<SessionSummaryResponse.ExerciseSummary> exerciseSummaries = exercises.stream()
                .map(e -> toExerciseSummary(e,
                        targetsByExercise.getOrDefault(e.getId(), List.of()),
                        recordsByExercise.getOrDefault(e.getId(), List.of()),
                        session))
                .toList();

        // ---------- 统计 ----------
        BigDecimal volume = BigDecimal.ZERO;
        int workingSets = 0;
        int warmupSets = 0;
        int totalReps = 0;
        int totalDurationSec = 0;
        int plannedSets = 0;

        for (SessionExercise exercise : exercises) {
            plannedSets += exercise.getTargetSets() == null ? 0 : exercise.getTargetSets();

            for (SetRecord record : recordsByExercise.getOrDefault(exercise.getId(), List.of())) {
                if (!TrainingMetrics.isWorkingSet(record.getSetType())) {
                    warmupSets++;
                    // ⚠️ 热身组的次数**不累加**进 totalReps。
                    //
                    // 容量和组数都排除了热身组，次数要是算进去，
                    // 用户会看到「正式组 2 组，共 30 次」——
                    // 而 2 组无论如何做不出 30 次，两个数字对不上。
                    // 要么全排除热身，要么全包含，不能只在一处例外。
                } else {
                    workingSets++;
                    if (record.getReps() != null && record.getReps() > 0) {
                        totalReps += record.getReps();
                    }
                }
                // 时长类动作单独统计（METRICS 4.1：折算不成重量×次数）
                if (exercise.getMetricType() == MetricType.DURATION
                        || exercise.getMetricType() == MetricType.DISTANCE_DURATION) {
                    totalDurationSec += record.getDurationSec() == null ? 0 : record.getDurationSec();
                }
                volume = volume.add(TrainingMetrics.setVolume(record, exercise, session));
            }
        }

        // ---------- 完成度 ----------
        int completed = (int) exercises.stream()
                .filter(e -> e.getStatus() == SessionExerciseStatus.COMPLETED).count();
        int skipped = (int) exercises.stream()
                .filter(e -> e.getStatus() == SessionExerciseStatus.SKIPPED).count();

        // ---------- PR ----------
        List<SessionSummaryResponse.PersonalRecordItem> prs =
                detectPersonalRecords(userId, session, exercises, recordsByExercise);

        // ---------- 与上次对比 ----------
        SessionSummaryResponse.Comparison comparison =
                compareWithPrevious(userId, session, exercises, recordsByExercise, volume, workingSets);

        return new SessionSummaryResponse(
                session.getId(),
                session.getDayName(),
                session.getWeekNumber(),
                session.isDeload(),
                session.getStartedAt(),
                session.getFinishedAt(),
                session.getDurationSec(),
                TrainingMetrics.round(volume),
                workingSets,
                warmupSets,
                totalReps,
                totalDurationSec,
                exercises.size(),
                completed,
                skipped,
                plannedSets,
                prs,
                exerciseSummaries,
                comparison);
    }

    // ==================================================================
    // PR 判定
    // ==================================================================

    /**
     * 找出本次训练刷新的个人纪录。
     *
     * <p>判定：某动作本次的**最佳组 e1RM** 超过它在本次之前的历史最高。
     *
     * <p>三个细节：
     * <ul>
     *   <li>取**最佳组**不取平均——见 {@link TrainingMetrics#bestE1rm}</li>
     *   <li>只对 {@code WEIGHT_REPS} 判定——自重和时长类没有 e1RM 概念</li>
     *   <li>历史值用 SQL 聚合（见 {@link SetRecordMapper#bestE1rmBefore}）</li>
     * </ul>
     */
    private List<SessionSummaryResponse.PersonalRecordItem> detectPersonalRecords(
            Long userId,
            WorkoutSession session,
            List<SessionExercise> exercises,
            Map<Long, List<SetRecord>> recordsByExercise) {

        // 本次各动作的最佳 e1RM
        Map<Long, BigDecimal> currentBest = new LinkedHashMap<>();
        Map<Long, String> nameById = new HashMap<>();

        for (SessionExercise exercise : exercises) {
            if (exercise.getExerciseId() == null
                    || exercise.getMetricType() != MetricType.WEIGHT_REPS) {
                continue;
            }
            BigDecimal best = recordsByExercise.getOrDefault(exercise.getId(), List.of())
                    .stream()
                    .map(r -> TrainingMetrics.bestE1rm(r, exercise))
                    .filter(Objects::nonNull)
                    .max(BigDecimal::compareTo)
                    .orElse(null);

            if (best != null) {
                currentBest.merge(exercise.getExerciseId(), best, BigDecimal::max);
                nameById.putIfAbsent(exercise.getExerciseId(), exercise.getExerciseName());
            }
        }

        if (currentBest.isEmpty()) {
            return List.of();
        }

        // 历史最佳（本次之前）
        Map<Long, BigDecimal> previousBest = setRecordMapper
                .bestE1rmBefore(userId, new ArrayList<>(currentBest.keySet()), session.getStartedAt())
                .stream()
                .collect(Collectors.toMap(
                        SetRecordMapper.ExerciseBestE1rm::exerciseId,
                        SetRecordMapper.ExerciseBestE1rm::bestE1rm));

        List<SessionSummaryResponse.PersonalRecordItem> result = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : currentBest.entrySet()) {
            Long exerciseId = entry.getKey();
            BigDecimal best = entry.getValue();
            BigDecimal previous = previousBest.get(exerciseId);

            // 第一次做这个动作也算 PR——用户确实创造了自己的第一个纪录
            if (previous == null || best.compareTo(previous) > 0) {
                result.add(new SessionSummaryResponse.PersonalRecordItem(
                        exerciseId,
                        nameById.get(exerciseId),
                        best,
                        previous,
                        previous == null ? null : best.subtract(previous)));
            }
        }
        return result;
    }

    // ==================================================================
    // 与上次对比
    // ==================================================================

    /**
     * 和**上一次同一个训练日**比。
     *
     * <p>不是「上一次随便什么训练」——推日和腿日的容量本来就不在一个量级，
     * 拿腿日跟推日比会让用户以为自己退步了。
     */
    private SessionSummaryResponse.Comparison compareWithPrevious(
            Long userId,
            WorkoutSession session,
            List<SessionExercise> exercises,
            Map<Long, List<SetRecord>> recordsByExercise,
            BigDecimal volume,
            int workingSets) {

        WorkoutSession previous = findPreviousSameDay(userId, session);
        if (previous == null) {
            return null;
        }

        List<SessionExercise> previousExercises = sessionExerciseMapper.selectList(
                new LambdaQueryWrapper<SessionExercise>()
                        .eq(SessionExercise::getSessionId, previous.getId())
                        .orderByAsc(SessionExercise::getOrderIndex));

        Map<Long, List<SetRecord>> previousRecords = loadRecords(
                previousExercises.stream().map(SessionExercise::getId).toList());

        // 上次的总量
        BigDecimal previousVolume = BigDecimal.ZERO;
        int previousSets = 0;
        for (SessionExercise exercise : previousExercises) {
            for (SetRecord record : previousRecords.getOrDefault(exercise.getId(), List.of())) {
                if (TrainingMetrics.isWorkingSet(record.getSetType())) {
                    previousSets++;
                }
                previousVolume = previousVolume.add(
                        TrainingMetrics.setVolume(record, exercise, previous));
            }
        }

        // 逐动作对比
        Map<Long, SessionExercise> currentByExerciseId = indexByExerciseId(exercises);
        Map<Long, SessionExercise> previousByExerciseId = indexByExerciseId(previousExercises);

        List<SessionSummaryResponse.ExerciseDelta> deltas = new ArrayList<>();
        for (Map.Entry<Long, SessionExercise> entry : currentByExerciseId.entrySet()) {
            SessionExercise previousExercise = previousByExerciseId.get(entry.getKey());
            if (previousExercise == null) {
                continue;   // 上次没有这个动作，比不了
            }

            SessionExercise currentExercise = entry.getValue();
            BigDecimal currentBestWeight = bestWorkingWeight(
                    recordsByExercise.getOrDefault(currentExercise.getId(), List.of()));
            BigDecimal previousBestWeight = bestWorkingWeight(
                    previousRecords.getOrDefault(previousExercise.getId(), List.of()));

            deltas.add(new SessionSummaryResponse.ExerciseDelta(
                    entry.getKey(),
                    currentExercise.getExerciseName(),
                    currentBestWeight,
                    previousBestWeight,
                    currentBestWeight == null || previousBestWeight == null
                            ? null : currentBestWeight.subtract(previousBestWeight),
                    volumeOf(currentExercise, recordsByExercise, session),
                    volumeOf(previousExercise, previousRecords, previous)));
        }

        return new SessionSummaryResponse.Comparison(
                previous.getId(),
                previous.getStartedAt(),
                TrainingMetrics.round(volume.subtract(previousVolume)),
                workingSets - previousSets,
                // 时长差：两次都记录了时长才有意义。
                // 用 0 当「没记录」会让差值变成一个假的巨大负数。
                session.getDurationSec() == null || previous.getDurationSec() == null
                        ? null : session.getDurationSec() - previous.getDurationSec(),
                deltas);
    }

    /**
     * 找上一次同计划、同训练日的**已完成**训练。
     *
     * <p>临时训练（{@code programId} 为 null）之间也可以比——
     * 它们没有「第几个训练日」，所以按「上一次临时训练」来。
     */
    private WorkoutSession findPreviousSameDay(Long userId, WorkoutSession session) {
        LambdaQueryWrapper<WorkoutSession> wrapper = new LambdaQueryWrapper<WorkoutSession>()
                .eq(WorkoutSession::getUserId, userId)
                .eq(WorkoutSession::getStatus, SessionStatus.COMPLETED)
                .lt(WorkoutSession::getStartedAt, session.getStartedAt())
                .orderByDesc(WorkoutSession::getStartedAt)
                .last("LIMIT 1");

        if (session.getProgramId() == null) {
            wrapper.isNull(WorkoutSession::getProgramId);
        } else {
            wrapper.eq(WorkoutSession::getProgramId, session.getProgramId())
                    .eq(WorkoutSession::getDayNumber, session.getDayNumber());
        }

        return sessionMapper.selectOne(wrapper);
    }

    // ==================================================================
    // 组装辅助
    // ==================================================================

    private SessionListItem toListItem(WorkoutSession session, StatsBundle bundle) {
        SessionStats stats = bundle.statsBySession().getOrDefault(session.getId(), SessionStats.EMPTY);
        return new SessionListItem(
                session.getId(),
                session.getStartedAt() == null ? null : session.getStartedAt().toLocalDate(),
                session.getStartedAt(),
                session.getDayName(),
                session.getWeekNumber(),
                session.isDeload(),
                session.getStatus() == null ? null : session.getStatus().name(),
                session.getStatus() == null ? null : session.getStatus().getDisplayName(),
                session.getDurationSec(),
                TrainingMetrics.round(stats.volume()),
                stats.workingSets(),
                bundle.exerciseCountBySession().getOrDefault(session.getId(), 0));
    }

    /** 一次会话的统计值 */
    private record SessionStats(BigDecimal volume, int workingSets) {
        static final SessionStats EMPTY = new SessionStats(BigDecimal.ZERO, 0);
    }

    /** 批量查询的结果包 */
    private record StatsBundle(Map<Long, SessionStats> statsBySession,
                               Map<Long, Integer> exerciseCountBySession) {
    }

    /**
     * 批量算出多场训练的统计值。
     *
     * <p>两次查询（动作快照 + 组记录），不在循环里逐会话查。
     */
    private StatsBundle loadStats(List<Long> sessionIds) {
        List<SessionExercise> exercises = sessionExerciseMapper.selectList(
                new LambdaQueryWrapper<SessionExercise>()
                        .in(SessionExercise::getSessionId, sessionIds));

        Map<Long, List<SetRecord>> recordsByExercise = loadRecords(
                exercises.stream().map(SessionExercise::getId).toList());

        // 会话 id → 会话（为了拿到快照体重）
        Map<Long, WorkoutSession> sessions = sessionMapper
                .selectList(new LambdaQueryWrapper<WorkoutSession>()
                        .in(WorkoutSession::getId, sessionIds))
                .stream().collect(Collectors.toMap(WorkoutSession::getId, s -> s));

        Map<Long, BigDecimal> volumeBySession = new HashMap<>();
        Map<Long, Integer> setsBySession = new HashMap<>();
        Map<Long, Integer> exerciseCount = new HashMap<>();

        for (SessionExercise exercise : exercises) {
            exerciseCount.merge(exercise.getSessionId(), 1, Integer::sum);

            WorkoutSession session = sessions.get(exercise.getSessionId());
            if (session == null) {
                continue;
            }
            for (SetRecord record : recordsByExercise.getOrDefault(exercise.getId(), List.of())) {
                if (TrainingMetrics.isWorkingSet(record.getSetType())) {
                    setsBySession.merge(exercise.getSessionId(), 1, Integer::sum);
                }
                volumeBySession.merge(exercise.getSessionId(),
                        TrainingMetrics.setVolume(record, exercise, session), BigDecimal::add);
            }
        }

        Map<Long, SessionStats> stats = new HashMap<>();
        for (Long id : sessionIds) {
            stats.put(id, new SessionStats(
                    volumeBySession.getOrDefault(id, BigDecimal.ZERO),
                    setsBySession.getOrDefault(id, 0)));
        }
        return new StatsBundle(stats, exerciseCount);
    }

    /**
     * 组装一个动作的明细：把计划与实际**按组号对齐**。
     *
     * <p>两边不是一对一，所以用组号的并集：
     * <pre>
     *   用户临时加组   → 有实际没计划
     *   用户少做几组   → 有计划没实际（done = false）
     * </pre>
     */
    private SessionSummaryResponse.ExerciseSummary toExerciseSummary(
            SessionExercise exercise,
            List<SessionSetTarget> targets,
            List<SetRecord> records,
            WorkoutSession session) {

        Map<Integer, SessionSetTarget> targetByNumber = targets.stream()
                .collect(Collectors.toMap(SessionSetTarget::getSetNumber, t -> t, (a, b) -> a));
        Map<Integer, SetRecord> recordByNumber = records.stream()
                .collect(Collectors.toMap(SetRecord::getSetNumber, r -> r, (a, b) -> a));

        // TreeSet 保证组号有序
        java.util.TreeSet<Integer> numbers = new java.util.TreeSet<>();
        numbers.addAll(targetByNumber.keySet());
        numbers.addAll(recordByNumber.keySet());

        List<SessionSummaryResponse.SetLine> lines = numbers.stream()
                .map(n -> toSetLine(n, targetByNumber.get(n), recordByNumber.get(n)))
                .toList();

        BigDecimal volume = records.stream()
                .map(r -> TrainingMetrics.setVolume(r, exercise, session))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SessionSummaryResponse.ExerciseSummary(
                exercise.getExerciseId(),
                exercise.getExerciseName(),
                exercise.getMetricType() == null ? null : exercise.getMetricType().name(),
                exercise.getStatus() == null ? null : exercise.getStatus().name(),
                exercise.getStatus() == null ? null : exercise.getStatus().getDisplayName(),
                exercise.getTargetSets() == null ? 0 : exercise.getTargetSets(),
                records.size(),
                TrainingMetrics.round(volume),
                lines);
    }

    private SessionSummaryResponse.SetLine toSetLine(int setNumber,
                                                     SessionSetTarget target,
                                                     SetRecord record) {

        // 组类型以**实际**为准：计划里是正式组，用户练到力竭会标成力竭组。
        // 只在没有实际记录时才回落到计划值。
        SetType type = (record != null && record.getSetType() != null)
                ? record.getSetType()
                : (target == null ? null : target.getSetType());

        return new SessionSummaryResponse.SetLine(
                setNumber,
                type == null ? null : type.name(),
                type == null ? null : type.getDisplayName(),

                target == null ? null : target.getTargetWeight(),
                target == null ? null : target.getTargetReps(),
                target == null ? null : target.getTargetRepsMin(),
                target == null ? null : target.getTargetRepsMax(),
                target == null ? null : target.getTargetDurationSec(),
                target == null ? null : target.getRestSec(),

                record != null,
                record == null ? null : record.getWeight(),
                record == null ? null : record.getReps(),
                record == null ? null : record.getDurationSec());
    }

    /** 一次查出所有动作的计划组目标 */
    private Map<Long, List<SessionSetTarget>> loadTargets(List<SessionExercise> exercises) {
        if (exercises.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = exercises.stream().map(SessionExercise::getId).toList();
        return sessionSetTargetMapper.selectList(
                        new LambdaQueryWrapper<SessionSetTarget>()
                                .in(SessionSetTarget::getSessionExerciseId, ids)
                                .orderByAsc(SessionSetTarget::getSetNumber))
                .stream()
                .collect(Collectors.groupingBy(SessionSetTarget::getSessionExerciseId));
    }

    private Map<Long, List<SetRecord>> loadRecords(List<Long> sessionExerciseIds) {
        if (sessionExerciseIds.isEmpty()) {
            return Map.of();
        }
        return setRecordMapper.selectList(
                        new LambdaQueryWrapper<SetRecord>()
                                .in(SetRecord::getSessionExerciseId, sessionExerciseIds)
                                .orderByAsc(SetRecord::getSetNumber))
                .stream()
                .collect(Collectors.groupingBy(SetRecord::getSessionExerciseId));
    }

    private Map<Long, SessionExercise> indexByExerciseId(List<SessionExercise> exercises) {
        Map<Long, SessionExercise> map = new LinkedHashMap<>();
        for (SessionExercise e : exercises) {
            if (e.getExerciseId() != null) {
                map.putIfAbsent(e.getExerciseId(), e);
            }
        }
        return map;
    }

    private BigDecimal bestWorkingWeight(List<SetRecord> records) {
        return records.stream()
                .filter(r -> TrainingMetrics.isWorkingSet(r.getSetType()))
                .map(SetRecord::getWeight)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal volumeOf(SessionExercise exercise,
                                Map<Long, List<SetRecord>> recordsByExercise,
                                WorkoutSession session) {
        return TrainingMetrics.round(
                recordsByExercise.getOrDefault(exercise.getId(), List.of())
                        .stream()
                        .map(r -> TrainingMetrics.setVolume(r, exercise, session))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
