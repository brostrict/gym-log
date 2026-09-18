package com.gymlog.demo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.body.BodyMetric;
import com.gymlog.body.BodyMetricMapper;
import com.gymlog.body.BodyMetricType;
import com.gymlog.body.BodyService;
import com.gymlog.body.BodySite;
import com.gymlog.body.MetricCondition;
import com.gymlog.body.dto.BodyMetricRequest;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.program.ProgramService;
import com.gymlog.program.TargetWeightType;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.training.SessionService;
import com.gymlog.training.SetRecordService;
import com.gymlog.training.SetType;
import com.gymlog.training.dto.SessionCreateRequest;
import com.gymlog.training.dto.SetRecordRequest;
import com.gymlog.user.User;
import com.gymlog.user.UserMapper;
import com.gymlog.user.UserService;
import com.gymlog.user.dto.RegisterRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 造演示数据 —— 12 周的训练历史。
 *
 * <h3>为什么必须有</h3>
 *
 * <p>图表要画「三个月趋势」，而真实库里只有十几场零散的会话（跨度 8 天）。
 * 不造数据的话：**既没法验证图表画得对不对，也没法演示**。
 *
 * <h3>为什么走 Service 调用而不是手写 INSERT</h3>
 *
 * <p>手写 SQL 会造出「看起来对、但不满足不变量」的数据，而且**不报错**：
 *
 * <ul>
 *   <li>{@code create()} 会按传入的 {@code date} <b>反算 {@code weekNumber} 和
 *       {@code isDeload}</b>——把第 6 周设成 deload，倒填的第 6 周会话自动带标记</li>
 *   <li>快照的 {@code bw_factor} / {@code primary_muscle} / {@code metric_type}
 *       全由 {@code insertSnapshot} 拷贝，手写必漏</li>
 *   <li>训练日的轮转依赖「已完成会话总数」，顺序错了会让训练日名和动作对不上</li>
 * </ul>
 *
 * <h3>⚠️ 三条硬约束</h3>
 *
 * <ol>
 *   <li><b>必须显式传 {@code startedAt}</b>。不传默认 {@code now()}，
 *       36 场训练会全挤在今天——日期全错、按周聚合全错，<b>而且不报错</b>。</li>
 *   <li><b>必须按时间顺序造</b>。轮转输入是「已完成会话总数」，倒序会让
 *       「第 1 周练 A 日」这种关系被破坏，而现象只是动作内容对不上，
 *       你会先去怀疑聚合代码。</li>
 *   <li><b>不能留 IN_PROGRESS</b>。否则 {@code findActive} 会把 demo 用户
 *       锁在「继续上次训练」里，再也开不了新会话。</li>
 * </ol>
 *
 * <h3>要造的「形状」——每一样都比数值本身值钱</h3>
 *
 * <table>
 *   <tr><td>一个<b>空周</b></td><td>{@code METRICS 4.6}：空周要显示 0 高度柱，不跳过</td></tr>
 *   <tr><td>一个 <b>&gt;14 天空档</b></td><td>{@code METRICS 1.5} 的断线规则</td></tr>
 *   <tr><td><b>热身组 + 力竭组</b></td><td>验证「热身不计入容量/组数」在图上看得见</td></tr>
 *   <tr><td><b>REPS_ONLY + DURATION 动作</b></td><td>否则自重和时长类的整条路径在 demo 里是空白</td></tr>
 * </table>
 *
 * <h3>用法</h3>
 * <pre>
 *   mvn spring-boot:run -Dspring-boot.run.arguments=--gymlog.seed-demo=true
 * </pre>
 *
 * <p><b>可重复执行</b>：每次先清掉 demo 用户的全部训练数据再重建。
 * 清理**严格限定在该用户**，不碰任何其他人的数据。
 */
@Component
@ConditionalOnProperty(name = "gymlog.seed-demo", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    static final String DEMO_EMAIL = "demo@gymlog.local";
    private static final String DEMO_PASSWORD = "Demo1234!";

    /** 12 周的起点。2026-06-29 是**周一**，第 12 周正好落在当前周 */
    private static final LocalDate START = LocalDate.of(2026, 6, 29);

    /** 空周的序号（1-based）。W4 是单周空档，W7–W8 连起来是 >14 天的空档 */
    private static final List<Integer> EMPTY_WEEKS = List.of(4, 7, 8);

    /** 每周练三次：周一 / 周三 / 周五 */
    private static final List<DayOfWeek> TRAINING_DAYS =
            List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

    private final UserService userService;
    private final UserMapper userMapper;
    private final ProgramService programService;
    private final SessionService sessionService;
    private final SetRecordService setRecordService;
    private final ExerciseMapper exerciseMapper;
    private final BodyService bodyService;
    private final BodyMetricMapper bodyMetricMapper;

    /**
     * 只用于**清理**。
     *
     * <p>为什么清理不用 Mapper 而是裸 SQL：见 {@link #purgeTrainingData} 的注释——
     * {@code workout_session} / {@code program} 配了逻辑删除，
     * Mapper 的 delete 只标 {@code deleted = 1}，物理行还在，
     * 而唯一索引不认那个标志，第二次跑种子就会撞键。
     */
    private final JdbcTemplate jdbcTemplate;

    public DemoDataSeeder(UserService userService, UserMapper userMapper,
                          ProgramService programService, SessionService sessionService,
                          SetRecordService setRecordService, ExerciseMapper exerciseMapper,
                          BodyService bodyService,
                          BodyMetricMapper bodyMetricMapper,
                          JdbcTemplate jdbcTemplate) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.programService = programService;
        this.sessionService = sessionService;
        this.setRecordService = setRecordService;
        this.exerciseMapper = exerciseMapper;
        this.bodyService = bodyService;
        this.bodyMetricMapper = bodyMetricMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        Long userId = ensureDemoUser();
        int purged = purgeTrainingData(userId);
        purgeBodyMetrics(userId);

        // ⚠️ 身体数据必须在会话之前造：会话创建时会按 startedAt
        // 快照当时的体重（V12 的 body_weight_kg），没有体重可取的话
        // 自重动作的容量会全是 0，而那看起来像「容量算错了」。
        int metrics = seedBodyMetrics(userId);

        Long programId = createProgram(userId);
        int sessions = seedSessions(userId, programId);

        log.info("""

                ================ 演示数据就绪 ================
                  账号     {} / {}
                  计划 id  {}
                  清理     {} 场旧会话
                  身体数据 {} 条（体重每日 + 心率/睡眠每周 + 围度每月）
                  新建     {} 场会话（12 周，每场带训练当天的体重快照）
                  空周     第 4 周（单周）、第 7–8 周（>14 天空档）
                ==============================================
                """, DEMO_EMAIL, DEMO_PASSWORD, programId, purged, metrics, sessions);
    }

    // ==================================================================

    private Long ensureDemoUser() {
        User existing = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, DEMO_EMAIL));
        if (existing != null) {
            return existing.getId();
        }
        RegisterRequest req = new RegisterRequest();
        req.setEmail(DEMO_EMAIL);
        req.setPassword(DEMO_PASSWORD);
        req.setNickname("演示账号");
        Long id = userService.register(req);
        log.info("已创建演示账号 {}（id={}）", DEMO_EMAIL, id);
        return id;
    }

    /**
     * 清掉 demo 用户的训练数据与计划，让种子**真的**可重复执行。
     *
     * <p><b>没有外键，必须自己按依赖顺序删。</b>全库零外键是有意的
     * （见 {@code REQUIREMENTS 8.4}：隔离靠应用层的 {@code user_id} 条件），
     * 代价就是删除也得自己写顺序。
     *
     * <h3>⚠️ 这里必须物理删除（{@code DELETE}），不能用 Mapper 的 delete</h3>
     *
     * <p>{@code workout_session} 和 {@code program} 都配了 MyBatis-Plus 的
     * <b>逻辑删除</b>（{@code logic-delete-field: deleted}）。走
     * {@code sessionMapper.delete(...)} 只会把它们标成 {@code deleted = 1}，
     * **物理行还在**——而唯一索引 {@code uk_session_client_key} 不认识
     * {@code deleted} 这个标志。
     *
     * <p>于是第二次跑种子时：清理「成功」了（日志还报「已清理 27 场」），
     * 紧接着插入 {@code demo-w1-d0} 就撞唯一键：
     *
     * <pre>
     *   Duplicate entry '63-demo-w1-d0' for key 'workout_session.uk_session_client_key'
     * </pre>
     *
     * <p>这个 bug 一直到**第二次**执行种子才暴露——第一次跑的时候库里
     * 没有旧数据，清理分支根本没进。所以「可重复执行」这句话当时是没验过的。
     *
     * <p>顺带：{@code program} 同样是逻辑删除，所以老代码连计划都没清，
     * 每跑一次多一个计划（现在一并删掉）。
     *
     * <p><b>为什么用 JdbcTemplate 而不是给 Mapper 加个 {@code @Delete}</b>：
     * 这是「演示数据清扫」，不是业务操作——业务上删会话是**软删**
     * （历史要留），只有这里需要物理抹掉。给生产 Mapper 加一个只有
     * 种子脚本会调的方法，等于把「有个地方会硬删数据」藏进数据访问层。
     * 写在这里，调用点就看得见。
     *
     * @return 删掉的会话数
     */
    private int purgeTrainingData(Long userId) {
        List<Long> sessionIds = jdbcTemplate.queryForList(
                "SELECT id FROM workout_session WHERE user_id = ?", Long.class, userId);
        if (sessionIds.isEmpty()) {
            // 计划仍要清：上一次跑可能只建了计划没建会话
            purgePrograms(userId);
            return 0;
        }

        // 按依赖顺序硬删：set_record → session_exercise → workout_session
        // （set_record / session_exercise 没有 deleted 列，本来就是物理删，
        //   这里一并走 JdbcTemplate 只是为了顺序和写法统一）
        jdbcTemplate.update("""
                DELETE sr FROM set_record sr
                JOIN session_exercise se ON se.id = sr.session_exercise_id
                WHERE se.session_id IN (%s)
                """.formatted(placeholders(sessionIds.size())), sessionIds.toArray());
        jdbcTemplate.update("DELETE FROM session_exercise WHERE session_id IN (%s)"
                .formatted(placeholders(sessionIds.size())), sessionIds.toArray());
        jdbcTemplate.update("DELETE FROM workout_session WHERE user_id = ?", userId);

        purgePrograms(userId);
        log.info("已清理演示账号的 {} 场旧会话（物理删除）", sessionIds.size());
        return sessionIds.size();
    }

    /**
     * 清掉 demo 用户的计划。
     *
     * <p>子表（{@code week_template} / {@code day_template} /
     * {@code prescribed_exercise} / {@code prescribed_set}）**没有**
     * {@code deleted} 列，所以走 Mapper 就是物理删——但顺序仍然要对。
     *
     * <p>⚠️ <b>「周」和「天」是两个独立维度，都直接挂在 {@code program_id} 上</b>，
     * {@code day_template} 上**没有** {@code week_template_id}。
     * 这一点很容易想当然：直觉上「一天属于某一周」，但这里
     * 「第 3 周的训练日」是 {@code week_template.week_number} 和
     * {@code day_template.day_number} 叉乘出来的，两者各存各的。
     */
    private void purgePrograms(Long userId) {
        List<Long> programIds = jdbcTemplate.queryForList(
                "SELECT id FROM program WHERE user_id = ?", Long.class, userId);
        if (programIds.isEmpty()) {
            return;
        }
        String in = placeholders(programIds.size());
        Object[] ids = programIds.toArray();

        jdbcTemplate.update("""
                DELETE ps FROM prescribed_set ps
                JOIN prescribed_exercise pe ON pe.id = ps.prescribed_exercise_id
                JOIN day_template dt ON dt.id = pe.day_template_id
                WHERE dt.program_id IN (%s)
                """.formatted(in), ids);
        jdbcTemplate.update("""
                DELETE pe FROM prescribed_exercise pe
                JOIN day_template dt ON dt.id = pe.day_template_id
                WHERE dt.program_id IN (%s)
                """.formatted(in), ids);
        jdbcTemplate.update("DELETE FROM day_template WHERE program_id IN (%s)"
                .formatted(in), ids);
        jdbcTemplate.update("DELETE FROM week_template WHERE program_id IN (%s)"
                .formatted(in), ids);
        jdbcTemplate.update("DELETE FROM program WHERE user_id = ?", userId);

        log.info("已清理演示账号的 {} 个旧计划（物理删除）", programIds.size());
    }

    /** 生成 {@code ?,?,?} —— 占位符个数必须和参数个数一致，手数是这类 bug 的常客 */
    private static String placeholders(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    /** 清掉 demo 用户的身体数据。和训练数据分开，因为它们的键不一样（没有外键可依） */
    private int purgeBodyMetrics(Long userId) {
        int n = bodyMetricMapper.delete(new LambdaQueryWrapper<BodyMetric>()
                .eq(BodyMetric::getUserId, userId));
        if (n > 0) {
            log.info("已清理演示账号的 {} 条身体数据", n);
        }
        return n;
    }

    // ==================================================================
    // 身体数据（4B）
    // ==================================================================

    /**
     * 造身体数据。
     *
     * <p><b>必须在 {@link #seedSessions} 之前跑。</b>
     * 会话创建时会调用 {@code BodyService.weightAsOf(userId, startedAt)}
     * 快照体重（V12 那一列的用途），而它是按 {@code measured_at <= startedAt}
     * 查的——所以体重记录得先存在，而且时间要铺满整个 12 周。
     *
     * <p>反过来说，这也意味着**会话的体重是按各自日期取的**，
     * 不是「都用最后一条」。第 1 周的会话记的是第 1 周的体重。
     *
     * <h3>要造的形状</h3>
     *
     * <table border="1">
     *   <caption>每个形状对应一条要验证的规则</caption>
     *   <tr><th>形状</th><th>为什么</th></tr>
     *   <tr><td>体重每天晨起空腹 + 每周一次训练后</td>
     *       <td>{@code METRICS 1.4}「条件不同的点用不同形状区分」、
     *           1.5「测量条件混杂」、以及日聚合把同一天两次合并成一个点</td></tr>
     *   <tr><td>日间噪声 ±1.2kg，真实趋势每周 −0.4kg</td>
     *       <td>{@code METRICS 1.3}：信噪比 1:2 到 1:4。
     *           原始点是锯齿、MA7 是平滑下降线——**这才看得出移动平均在干活**</td></tr>
     *   <tr><td>W4 / W7 / W8 完全不测</td>
     *       <td>{@code METRICS 1.5}「连续 14 天无数据 → 曲线断开」</td></tr>
     *   <tr><td>围度只测 5 个部位、且是月度</td>
     *       <td>{@code M6-A-3}「支持只填部分部位」+ {@code METRICS 2.5}「只显示有数据的部位」</td></tr>
     *   <tr><td>体脂秤五项带设备型号</td>
     *       <td>{@code M6-B-6}：换秤会让趋势线出现假跳变，所以要记型号</td></tr>
     * </table>
     */
    private int seedBodyMetrics(Long userId) {
        // 固定种子 → 可重复执行时噪声序列一致。
        // 不固定的话每次跑出来的曲线都不一样，「昨天看着对的图今天变了」很难排查。
        Random rnd = new Random(42);

        LocalDate today = LocalDate.now();
        int count = 0;

        for (int week = 1; week <= 12; week++) {
            if (EMPTY_WEEKS.contains(week)) {
                continue;   // 完全断档：验 14 天断线
            }
            LocalDate weekStart = START.plusWeeks(week - 1L);

            for (int d = 0; d < 7; d++) {
                LocalDate date = weekStart.plusDays(d);
                if (date.isAfter(today)) {
                    break;
                }
                if (d == 6) {
                    continue;   // 周日不称——真实用户不会天天称
                }

                long dayIndex = ChronoUnit.DAYS.between(START, date);
                // 真实趋势：12 周减 4.8kg（约每周 0.4kg，对应每日 ~440 kcal 缺口）
                double base = 78.0 - 0.40 * dayIndex / 7.0;

                // 晨起空腹：±1.2kg 日内噪声。噪声比信号大——这正是必须做 MA 的原因
                record(userId, BodyMetricType.WEIGHT, BodySite.NONE,
                        base + (rnd.nextDouble() - 0.5) * 2.4,
                        date.atTime(7, 0), MetricCondition.FASTED, null);
                count++;

                // 周三训练后再称一次：脱水 + 糖原消耗，读数偏低。
                // 同一天两个条件 → 日聚合要把它们合成一个点（METRICS 1.2 第一步）
                if (d == 2) {
                    record(userId, BodyMetricType.WEIGHT, BodySite.NONE,
                            base - 1.1 + (rnd.nextDouble() - 0.5) * 0.6,
                            date.atTime(21, 30), MetricCondition.POST_WORKOUT, null);
                    count++;
                }
            }

            // ---------- 每周一次：静息心率 + 睡眠质量 ----------
            if (!weekStart.isAfter(today)) {
                // 静息心率：M6-C-1 要求晨起，所以条件必须带
                record(userId, BodyMetricType.RESTING_HR, BodySite.NONE,
                        62 - 0.4 * (week - 1) + (rnd.nextDouble() - 0.5) * 4,
                        weekStart.atTime(7, 2), MetricCondition.FASTED, null);
                record(userId, BodyMetricType.SLEEP_QUALITY, BodySite.NONE,
                        3 + rnd.nextInt(3), weekStart.atTime(7, 10), null, null);
                count += 2;
            }
        }

        // ---------- 围度：月度，且**只测 5 个部位** ----------
        //
        // M6-A-3 允许只填部分部位，METRICS 2.5 要求只显示有数据的部位。
        // 12 个部位全造的话，「切换器只列出有数据的」这条就验不到了。
        for (int week : List.of(1, 5, 9, 13)) {
            LocalDate date = START.plusWeeks(week - 1L);
            if (date.isAfter(today)) {
                break;
            }
            LocalDateTime at = date.atTime(7, 15);
            double months = (week - 1) / 4.0;
            record(userId, BodyMetricType.CIRCUMFERENCE, BodySite.WAIST,
                    86.0 - 2.2 * months, at, null, null);
            record(userId, BodyMetricType.CIRCUMFERENCE, BodySite.CHEST,
                    101.0 - 1.0 * months, at, null, null);
            record(userId, BodyMetricType.CIRCUMFERENCE, BodySite.HIP,
                    98.0 - 1.6 * months, at, null, null);
            record(userId, BodyMetricType.CIRCUMFERENCE, BodySite.LEFT_UPPER_ARM,
                    34.0 + 0.6 * months, at, null, null);
            record(userId, BodyMetricType.CIRCUMFERENCE, BodySite.RIGHT_UPPER_ARM,
                    34.4 + 0.6 * months, at, null, null);
            count += 5;
        }

        log.info("已造 {} 条身体数据", count);
        return count;
    }

    /**
     * 写一条身体数据。
     *
     * <p><b>走 Service 而不是直接 insert</b>——和训练数据同一个理由：
     * {@code record()} 里有 UPSERT、量程校验、site 白名单，
     * 手写 INSERT 会绕开它们，造出「看起来对、但违反不变量」的数据且不报错。
     */
    private void record(Long userId, BodyMetricType type, BodySite site, double value,
                        LocalDateTime at, MetricCondition condition, String note) {
        bodyService.record(userId, new BodyMetricRequest(
                type,
                site == BodySite.NONE ? null : site,
                BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP),
                at,
                condition,
                note));
    }

    /**
     * 三天分化：推 / 拉 / 腿。
     *
     * <p>刻意混入三种 {@code metric_type}——只造 {@code WEIGHT_REPS} 的话，
     * 为自重和时长类做的工作在演示里**完全看不见**。
     */
    private Long createProgram(Long userId) {
        return programService.create(userId, new ProgramCreateRequest(
                "演示计划（推拉腿 · 12 周）",
                "造数据脚本生成的演示计划，含渐进超负荷与 deload 周",
                12, START,
                weeks(),
                List.of(
                        day(1, "推日", List.of(
                                prescription("杠铃卧推", 1, 4, 5, 8, 120, "60"),
                                prescription("站姿杠铃推举", 2, 3, 6, 10, 90, "35"),
                                prescription("双杠臂屈伸", 3, 3, 8, 12, 90, null))),
                        day(2, "拉日", List.of(
                                prescription("杠铃划船", 1, 4, 6, 10, 120, "50"),
                                prescription("硬拉", 2, 3, 5, 5, 180, "80"),
                                durationPrescription("平板支撑", 3, 3, 45, 60, null, 45))),
                        day(3, "腿日", List.of(
                                prescription("杠铃深蹲", 1, 5, 5, 5, 180, "70"),
                                prescription("罗马尼亚硬拉", 2, 3, 8, 10, 120, "60"),
                                prescription("卷腹", 3, 3, 12, 20, 60, null))))));
    }

    /** 12 周，第 6 周是 deload */
    private List<ProgramCreateRequest.WeekRequest> weeks() {
        List<ProgramCreateRequest.WeekRequest> weeks = new ArrayList<>(12);
        for (int w = 1; w <= 12; w++) {
            boolean deload = w == 6;
            weeks.add(new ProgramCreateRequest.WeekRequest(
                    w, 3,
                    deload ? new BigDecimal("-40") : BigDecimal.ZERO,
                    deload ? -1 : 0,
                    deload,
                    deload ? "减量周" : null));
        }
        return weeks;
    }

    private ProgramCreateRequest.DayRequest day(int number, String name,
                                                List<ProgramCreateRequest.PrescriptionRequest> items) {
        return new ProgramCreateRequest.DayRequest(number, name, false, null, items);
    }

    private ProgramCreateRequest.PrescriptionRequest prescription(
            String exerciseName, int order, int sets, int repsMin, int repsMax,
            int restSec, String weight) {
        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId(exerciseName), order, null, null, sets, repsMin, repsMax, restSec,
                TargetWeightType.ABSOLUTE,
                weight == null ? null : new BigDecimal(weight),
                null, null,
                // 自重/时长类没有目标时长，这两个字段留空
                null, null,
                null, List.of());
    }

    /** 时长类动作：目标 45 秒、每 15 秒播报一次 */
    private ProgramCreateRequest.PrescriptionRequest durationPrescription(
            String exerciseName, int order, int sets, int repsMin, int repsMax,
            String weight, int durationSec) {
        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId(exerciseName), order, null, null, sets, repsMin, repsMax, 60,
                TargetWeightType.ABSOLUTE,
                weight == null ? null : new BigDecimal(weight),
                null, null, durationSec, 15, null, List.of());
    }

    // ==================================================================

    /**
     * 按时间顺序造 12 周的训练。
     *
     * <p>进度按周线性递增（每周 +2.5%），deload 周回落 40%——
     * 这样 e1RM 曲线是一条带一个凹口的上升线，一眼能看出「这图是对的」。
     */
    private int seedSessions(Long userId, Long programId) {
        int count = 0;
        for (int week = 1; week <= 12; week++) {
            if (EMPTY_WEEKS.contains(week)) {
                continue;   // 空周：故意不练，验证零填充与断线
            }
            LocalDate weekStart = START.plusWeeks(week - 1L);
            for (int i = 0; i < TRAINING_DAYS.size(); i++) {
                LocalDate date = weekStart.with(TRAINING_DAYS.get(i));
                seedOneSession(userId, programId, date, week, i);
                count++;
            }
        }
        return count;
    }

    private void seedOneSession(Long userId, Long programId,
                                LocalDate date, int week, int dayIndex) {
        // ⚠️ startedAt 显式给：不传默认 now()，36 场会全挤在今天
        SessionDetailResponseShim created = createSession(userId, programId, date, week, dayIndex);

        for (var exercise : created.exercises()) {
            int progressionPct = week == 6 ? -40 : (week - 1) * 2;
            seedExercise(userId, created.sessionId(), exercise, date, week, progressionPct);
        }
        sessionService.finish(userId, created.sessionId(), null, null);
    }

    private record SessionDetailResponseShim(Long sessionId, List<ExerciseShim> exercises) {
    }

    private record ExerciseShim(Long id, String name, String metricType, int targetSets) {
    }

    private SessionDetailResponseShim createSession(Long userId, Long programId,
                                                    LocalDate date, int week, int dayIndex) {
        var detail = sessionService.create(userId, new SessionCreateRequest(
                programId,
                // 训练日轮转：周一/三/五 → 第 1/2/3 个训练日
                (dayIndex % 3) + 1,
                date,
                "demo-w%d-d%d".formatted(week, dayIndex),
                date.atTime(19, 0)));
        List<ExerciseShim> exercises = detail.exercises().stream()
                .map(e -> new ExerciseShim(e.id(), e.exerciseName(), e.metricType(), e.targetSets()))
                .toList();
        return new SessionDetailResponseShim(detail.id(), exercises);
    }

    /**
     * 记一场训练里某个动作的所有组。
     *
     * <p>每场第一个动作先来一组**热身**——它的重量是正式组的 60%。
     * 不造热身组的话，「热身不计入容量和组数」这条规则在图上就看不出来。
     */
    private void seedExercise(Long userId, Long sessionId, ExerciseShim exercise,
                              LocalDate date, int week, int progressionPct) {
        int sets = exercise.targetSets();
        double base = baseWeight(exercise.name());
        double weight = base * (1 + progressionPct / 100.0);

        for (int n = 1; n <= sets; n++) {
            SetType type = (n == 1 && "杠铃深蹲".equals(exercise.name()))
                    ? SetType.WARMUP : SetType.WORKING;
            double w = type == SetType.WARMUP ? weight * 0.6 : weight;

            // 时长类：记秒数；次数类：记次数
            Integer reps = "DURATION".equals(exercise.metricType()) ? null : repsFor(exercise, n);
            Integer durationSec = "DURATION".equals(exercise.metricType())
                    ? 45 + (week - 1) * 2 : null;

            setRecordService.recordSet(userId, sessionId, exercise.id(), n,
                    new SetRecordRequest(type,
                            "REPS_ONLY".equals(exercise.metricType()) ? null : BigDecimal.valueOf(w),
                            reps, durationSec,
                            null,   // distanceM
                            null,   // rpe
                            null,   // restActualSec
                            null,   // note
                            date.atTime(19, n * 3)));
        }
    }

    private int repsFor(ExerciseShim exercise, int setNumber) {
        return switch (exercise.name()) {
            case "杠铃划船" -> 8;
            case "站姿杠铃推举" -> 8;
            case "罗马尼亚硬拉" -> 10;
            default -> 5;
        };
    }

    /** 各动作的基准重量（第 1 周） */
    private double baseWeight(String exerciseName) {
        return switch (exerciseName) {
            case "杠铃深蹲" -> 70;
            case "杠铃卧推" -> 60;
            case "硬拉" -> 80;
            case "杠铃划船" -> 50;
            case "站姿杠铃推举" -> 35;
            case "罗马尼亚硬拉" -> 60;
            default -> 0;
        };
    }

    /**
     * 动作按**名称**查——id 各环境不一致，写死 id 会让脚本换个库就跑不了。
     *
     * <p><b>查不到直接抛异常，不静默跳过。</b>这里踩过一次：
     * 挑选动作时用 {@code WHERE name IN ('杠铃深蹲','杠铃卧推',...)} 列了 10 个名字，
     * 结果只回来 9 个——「站姿推举」其实叫「站姿杠铃推举」。
     * {@code IN} 查询里不存在的项**只是不出现在结果里**，不会报错，
     * 所以扫一眼结果很容易以为全都在。
     *
     * <p>好在查不到时是**启动即失败**、而不是少造一个动作继续跑——
     * 后者会造出一份「看起来正常、但少练了一个部位」的演示数据，
     * 而你要过很久才会发现图不对。
     */
    private Long exerciseId(String name) {
        Exercise e = exerciseMapper.selectOne(new LambdaQueryWrapper<Exercise>()
                .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID)
                .eq(Exercise::getName, name));
        if (e == null) {
            throw new IllegalStateException("动作库里没有「" + name + "」，演示数据脚本无法继续");
        }
        return e.getId();
    }
}
