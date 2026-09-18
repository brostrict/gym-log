package com.gymlog.stats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.exercise.MetricType;
import com.gymlog.exercise.MuscleGroup;
import com.gymlog.stats.dto.ExerciseE1rmResponse;
import com.gymlog.stats.dto.PrBoardResponse;
import com.gymlog.stats.dto.WeeklyStatsResponse;
import com.gymlog.training.SessionExercise;
import com.gymlog.training.SessionExerciseMapper;
import com.gymlog.training.SessionSetTarget;
import com.gymlog.training.SessionSetTargetMapper;
import com.gymlog.training.SessionStatus;
import com.gymlog.training.SetRecord;
import com.gymlog.training.SetRecordMapper;
import com.gymlog.training.TrainingMetrics;
import com.gymlog.training.WorkoutSession;
import com.gymlog.training.WorkoutSessionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 统计聚合。
 *
 * <h3>聚合走 Java 还是 SQL —— 按「有没有界」分</h3>
 *
 * <table>
 *   <tr><td>有界区间（周序列、单动作曲线）</td><td><b>Java 批查</b></td></tr>
 *   <tr><td>无界全史（PR 看板）</td><td><b>SQL 窗口函数</b>（见 {@link StatsMapper}）</td></tr>
 * </table>
 *
 * <p>有界区间走 Java 的理由是**口径只有一份**：容量依赖
 * {@code metric_type} + {@code bw_factor} + 快照体重三字段交叉判断，
 * 写进 SQL 就是一大段 {@code CASE WHEN}，而且会和 {@link TrainingMetrics} 分叉。
 * {@code SessionSummaryService} 的类注释早就论证过这一点，这里沿用。
 *
 * <p>还有一条实践理由：{@code set_record} 没有 {@code deleted} 列而
 * {@code workout_session} 有，MyBatis-Plus 的逻辑删除只对
 * {@code LambdaQueryWrapper} 生效 —— 走 Java 批查等于让框架替你守这条不变量。
 *
 * <h3>⚠️ 所有方法的 {@code today} / {@code from} / {@code to} 都是参数</h3>
 *
 * <p>没有一处调 {@code LocalDate.now()}。{@code now()} 只允许出现在 Controller 层
 * ——这是 {@code TrainingSchedule} 定下的规矩（「否则测试就没法固定时间了」）。
 */
@Service
public class StatsService {

    /** 一次查询最多覆盖多少天。约 2 年，超出则夹到上限 */
    public static final int MAX_RANGE_DAYS = 730;

    private final WorkoutSessionMapper sessionMapper;
    private final SessionExerciseMapper sessionExerciseMapper;
    private final SetRecordMapper setRecordMapper;
    private final SessionSetTargetMapper setTargetMapper;
    private final ExerciseMapper exerciseMapper;
    private final StatsMapper statsMapper;

    public StatsService(WorkoutSessionMapper sessionMapper,
                        SessionExerciseMapper sessionExerciseMapper,
                        SetRecordMapper setRecordMapper,
                        SessionSetTargetMapper setTargetMapper,
                        ExerciseMapper exerciseMapper,
                        StatsMapper statsMapper) {
        this.sessionMapper = sessionMapper;
        this.sessionExerciseMapper = sessionExerciseMapper;
        this.setRecordMapper = setRecordMapper;
        this.setTargetMapper = setTargetMapper;
        this.exerciseMapper = exerciseMapper;
        this.statsMapper = statsMapper;
    }

    // ==================================================================
    // 一、周序列：容量 + 组数（按肌群）+ 频率 + 符合率
    // ==================================================================

    @Transactional(readOnly = true)
    public WeeklyStatsResponse weekly(Long userId, LocalDate from, LocalDate to, LocalDate today) {
        LocalDate end = clampEnd(from, to);

        List<WorkoutSession> sessions = completedSessions(userId, from, end);

        // 周桶**按日历铺开**（含空周），不是「查出有数据的周」。
        // METRICS 4.6：空周要显示 0 高度柱，跳过会掩盖中断。
        // 用 TreeMap 保证升序——HashMap 的话 JSON 里周顺序随机，
        // 客户端 X 轴会画成锯齿，断言顺序还会 flaky。
        Map<LocalDate, WeekAcc> byWeek = new TreeMap<>();
        for (LocalDate weekStart : WeekSeries.weekStarts(from, end)) {
            byWeek.put(weekStart, new WeekAcc(weekStart));
        }
        if (byWeek.isEmpty()) {
            return new WeeklyStatsResponse(from, end, 0, List.of());
        }

        Map<Long, WorkoutSession> sessionById = new HashMap<>();
        Map<LocalDate, Integer> sessionCountByWeek = new HashMap<>();
        for (WorkoutSession s : sessions) {
            sessionById.put(s.getId(), s);
            LocalDate week = WeekSeries.weekStart(s.getStartedAt().toLocalDate());
            if (byWeek.containsKey(week)) {
                byWeek.get(week).sessionCount++;
                sessionCountByWeek.merge(week, 1, Integer::sum);
            }
        }

        // streak 由服务端算——「连续几周」是一条口径规则，
        // 不是把数组长度一数就完事的（当前周怎么算、缺一周算不算断都有定义）
        int streak = WeekSeries.currentStreak(sessionCountByWeek, today);

        if (!sessionById.isEmpty()) {
            aggregateSets(userId, List.copyOf(sessionById.values()), sessionById, byWeek);
        }

        List<WeeklyStatsResponse.WeekBucket> weeks = new ArrayList<>(byWeek.size());
        for (WeekAcc acc : byWeek.values()) {
            weeks.add(new WeeklyStatsResponse.WeekBucket(
                    acc.weekStart,
                    WeekSeries.isCurrentWeek(acc.weekStart, today),
                    TrainingMetrics.round(acc.volume),
                    acc.workingSets,
                    acc.sessionCount,
                    acc.compliance(),
                    acc.muscleSets()));
        }
        return new WeeklyStatsResponse(from, end, streak, weeks);
    }

    /** 遍历范围内的动作与组记录，累加进各周桶。两次 IN 批查，不在循环里逐会话查 */
    private void aggregateSets(Long userId,
                               List<WorkoutSession> sessions,
                               Map<Long, WorkoutSession> sessionById,
                               Map<LocalDate, WeekAcc> byWeek) {
        List<Long> sessionIds = sessions.stream().map(WorkoutSession::getId).toList();

        List<SessionExercise> exercises = sessionExerciseMapper.selectList(
                new LambdaQueryWrapper<SessionExercise>()
                        .in(SessionExercise::getSessionId, sessionIds));
        if (exercises.isEmpty()) {
            return;
        }

        Map<Long, List<SetRecord>> recordsByExercise = loadRecords(
                exercises.stream().map(SessionExercise::getId).toList());
        Map<Long, Integer> plannedByExercise = loadPlannedCounts(
                exercises.stream().map(SessionExercise::getId).toList());

        for (SessionExercise exercise : exercises) {
            WorkoutSession session = sessionById.get(exercise.getSessionId());
            if (session == null) {
                continue;
            }
            LocalDate week = WeekSeries.weekStart(session.getStartedAt().toLocalDate());
            WeekAcc acc = byWeek.get(week);
            if (acc == null) {
                continue;
            }

            int actual = 0;
            for (SetRecord record : recordsByExercise.getOrDefault(exercise.getId(), List.of())) {
                acc.volume = acc.volume.add(
                        TrainingMetrics.setVolume(record, exercise, session));
                if (TrainingMetrics.isWorkingSet(record.getSetType())) {
                    acc.workingSets++;
                    actual++;
                    acc.addMuscleSets(exercise.getPrimaryMuscle(), 1);
                }
            }

            // 符合率：计划目标取**快照**的逐组目标条数。
            // 用快照而不是现查计划——用户改了计划，历史的执行度不该跟着变。
            Integer planned = plannedByExercise.get(exercise.getId());
            if (planned != null && planned > 0) {
                acc.compliance.add(new Adherence.PlannedActual(planned, actual));
            }
        }
    }

    private Map<Long, List<SetRecord>> loadRecords(List<Long> sessionExerciseIds) {
        return setRecordMapper.selectList(
                        new LambdaQueryWrapper<SetRecord>()
                                .in(SetRecord::getSessionExerciseId, sessionExerciseIds)
                                .orderByAsc(SetRecord::getSetNumber))
                .stream().collect(java.util.stream.Collectors.groupingBy(SetRecord::getSessionExerciseId));
    }

    private Map<Long, Integer> loadPlannedCounts(List<Long> sessionExerciseIds) {
        Map<Long, Integer> counts = new HashMap<>();
        for (SessionSetTarget t : setTargetMapper.selectList(
                new LambdaQueryWrapper<SessionSetTarget>()
                        .in(SessionSetTarget::getSessionExerciseId, sessionExerciseIds))) {
            counts.merge(t.getSessionExerciseId(), 1, Integer::sum);
        }
        return counts;
    }

    // ==================================================================
    // 二、单动作 e1RM 曲线
    // ==================================================================

    @Transactional(readOnly = true)
    public ExerciseE1rmResponse e1rm(Long userId, Long exerciseId,
                                     LocalDate from, LocalDate to) {
        Exercise exercise = exerciseMapper.selectById(exerciseId);

        // METRICS 3.6：metric_type != WEIGHT_REPS 的动作**不显示此图**
        //（自重、时长类没有「1RM」这个概念）。返回 supported=false 让客户端
        // 显示一句说明，而不是一张空图——空图看起来像「还没练」。
        boolean supported = exercise != null && exercise.getMetricType() == MetricType.WEIGHT_REPS;
        String name = exercise == null ? null : exercise.getName();
        String metric = exercise == null || exercise.getMetricType() == null
                ? null : exercise.getMetricType().name();

        if (!supported) {
            return new ExerciseE1rmResponse(exerciseId, name, metric, false, null, List.of());
        }

        LocalDate end = clampEnd(from, to);
        List<WorkoutSession> sessions = completedSessions(userId, from, end);
        Map<Long, WorkoutSession> sessionById = new HashMap<>();
        for (WorkoutSession s : sessions) {
            sessionById.put(s.getId(), s);
        }

        List<ExerciseE1rmResponse.E1rmPoint> points = new ArrayList<>();
        if (!sessionById.isEmpty()) {
            List<SessionExercise> own = sessionExerciseMapper.selectList(
                    new LambdaQueryWrapper<SessionExercise>()
                            .in(SessionExercise::getSessionId, sessionById.keySet())
                            .eq(SessionExercise::getExerciseId, exerciseId));
            Map<Long, List<SetRecord>> records = loadRecords(
                    own.stream().map(SessionExercise::getId).toList());

            for (SessionExercise se : own) {
                WorkoutSession session = sessionById.get(se.getSessionId());
                if (session == null) {
                    continue;
                }
                buildPoint(se, records.getOrDefault(se.getId(), List.of()), session)
                        .ifPresent(points::add);
            }
            points.sort(Comparator.comparing(ExerciseE1rmResponse.E1rmPoint::date));
        }

        return new ExerciseE1rmResponse(exerciseId, name, metric, true,
                allTimeBest(userId, exerciseId), points);
    }

    /**
     * 一次训练的数据点。
     *
     * <p>{@code METRICS 3.5}：「本次训练所有组 {@code reps > 12} → **该次不参与曲线**」。
     * {@link TrainingMetrics#bestE1rm} 对超次数的组返回 null，所以某次的最佳组为 null
     * 就说明整次都没有有效组——跳过这个点，而不是画一个 0。
     */
    private java.util.Optional<ExerciseE1rmResponse.E1rmPoint> buildPoint(
            SessionExercise se, List<SetRecord> records, WorkoutSession session) {

        BigDecimal best = null;
        List<ExerciseE1rmResponse.SetPoint> sets = new ArrayList<>();

        for (SetRecord r : records) {
            BigDecimal one = TrainingMetrics.bestE1rm(r, se);
            if (one == null) {
                continue;
            }
            sets.add(new ExerciseE1rmResponse.SetPoint(r.getWeight(), r.getReps(), one));
            if (best == null || one.compareTo(best) > 0) {
                best = one;
            }
        }

        // METRICS 3.5：数据点 < 2 只显示散点不连线——那是客户端的事，
        // 这里只保证「有有效组才给点」
        if (best == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ExerciseE1rmResponse.E1rmPoint(
                session.getStartedAt().toLocalDate(), best, sets));
    }

    /**
     * 历史最高 e1RM，画参考虚线用。
     *
     * <p><b>复用既有的 SQL 聚合，不新写公式</b>：{@code bestE1rmBefore} 传一个
     * 远未来的时间点就是「该动作历史最高」。零新 SQL、零第三份公式，
     * 而且它已经被 {@code sqlE1rmMatchesJavaE1rm} 契约测试守着。
     */
    private BigDecimal allTimeBest(Long userId, Long exerciseId) {
        var rows = setRecordMapper.bestE1rmBefore(
                userId, List.of(exerciseId), LocalDateTime.of(9999, 12, 31, 0, 0));
        if (rows.isEmpty()) {
            return null;
        }
        // ⚠️ 必须 round。那条 SQL 里的 `MAX(weight * (1 + reps / 30))` **不做小数位处理**，
        // 而 Java 侧的 `TrainingMetrics.e1rm` 有 `.setScale(2, HALF_UP)`。
        // 不补这一步，参考线会写 81.666667 而曲线上的点是 81.67——
        // 同一个数两个写法，用户会以为它们不是一回事。
        //
        // （契约测试 `sqlE1rmMatchesJavaE1rm` 用的是 `isEqualByComparingTo` 数值比较，
        //  所以它一直是绿的，掩盖了这个显示层的不一致。）
        return TrainingMetrics.round(rows.get(0).bestE1rm());
    }

    // ==================================================================
    // 三、PR 看板
    // ==================================================================

    @Transactional(readOnly = true)
    public PrBoardResponse prs(Long userId, LocalDate today) {
        List<PrBoardResponse.PrCard> cards = new ArrayList<>();

        for (StatsMapper.PrCandidate c : statsMapper.maxWeightPerExercise(userId)) {
            cards.add(toCard(c, "MAX_WEIGHT", "最大重量", "kg", today));
        }
        for (StatsMapper.PrCandidate c : statsMapper.bestE1rmPerExercise(userId)) {
            cards.add(toCard(c, "BEST_E1RM", "最佳 e1RM", "kg", today));
        }

        // METRICS 7.3：按达成日期倒序（最近的在前）
        cards.sort(Comparator.comparing(PrBoardResponse.PrCard::achievedOn).reversed());
        return new PrBoardResponse(cards);
    }

    private PrBoardResponse.PrCard toCard(StatsMapper.PrCandidate c,
                                          String metric, String label, String unit,
                                          LocalDate today) {
        LocalDate achievedOn = c.achievedAt().toLocalDate();
        long daysAgo = ChronoUnit.DAYS.between(achievedOn, today);
        boolean firstTime = c.firstAt() != null
                && c.firstAt().toLocalDate().equals(achievedOn);

        return new PrBoardResponse.PrCard(
                c.exerciseId(),
                c.exerciseName(),
                metric,
                label,
                c.value().setScale(2, RoundingMode.HALF_UP),
                unit,
                achievedOn,
                Math.max(daysAgo, 0),
                firstTime);
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private List<WorkoutSession> completedSessions(Long userId, LocalDate from, LocalDate to) {
        // 范围过滤用 started_at（METRICS 0.2：训练跨零点时以**开始时间**归属日期），
        // 不能用 set_record.completed_at——那既和 0.2 冲突，又没有索引
        return sessionMapper.selectList(new LambdaQueryWrapper<WorkoutSession>()
                .eq(WorkoutSession::getUserId, userId)
                .eq(WorkoutSession::getStatus, SessionStatus.COMPLETED)
                .ge(WorkoutSession::getStartedAt, from.atStartOfDay())
                .lt(WorkoutSession::getStartedAt, to.plusDays(1).atStartOfDay()));
    }

    /** 把区间上限夹到 {@link #MAX_RANGE_DAYS} 以内，防止一次拉穿整个历史 */
    private LocalDate clampEnd(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return from == null ? LocalDate.now() : from;
        }
        LocalDate limit = from.plusDays(MAX_RANGE_DAYS);
        return to.isAfter(limit) ? limit : to;
    }

    /** 一周的累加器。可变，但生命周期只在这个方法内 */
    private static final class WeekAcc {
        final LocalDate weekStart;
        BigDecimal volume = BigDecimal.ZERO;
        int workingSets;
        int sessionCount;
        final List<Adherence.PlannedActual> compliance = new ArrayList<>();
        final Map<MuscleGroup, Integer> muscleSets = new EnumMap<>(MuscleGroup.class);

        WeekAcc(LocalDate weekStart) {
            this.weekStart = weekStart;
        }

        void addMuscleSets(MuscleGroup muscle, int n) {
            if (muscle != null) {
                muscleSets.merge(muscle, n, Integer::sum);
            }
        }

        BigDecimal compliance() {
            return Adherence.complianceRate(compliance);
        }

        /**
         * 固定 6 项、含 0——缺项会让客户端的颜色映射错位。
         *
         * <p>⚠️ <b>用 {@link MuscleGroup#muscles()} 而不是 {@code values()}。</b>
         * 枚举里还有热身和拉伸，它们**不是肌群**——放进这张图会和
         * 「10–20 组/周」的增肌参考区间并列，而 6 组拉伸和 6 组深蹲毫无可比性。
         * 见 {@link MuscleGroup#isMuscle()}。
         */
        List<WeeklyStatsResponse.MuscleSets> muscleSets() {
            MuscleGroup[] muscles = MuscleGroup.muscles();
            List<WeeklyStatsResponse.MuscleSets> out = new ArrayList<>(muscles.length);
            for (MuscleGroup m : muscles) {
                out.add(new WeeklyStatsResponse.MuscleSets(
                        m.name(), m.getDisplayName(), muscleSets.getOrDefault(m, 0)));
            }
            return out;
        }
    }
}
