package com.gymlog.body;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.body.dto.BodyMetricRequest;
import com.gymlog.body.dto.BodyMetricResponse;
import com.gymlog.body.dto.BodyMetricTypeResponse;
import com.gymlog.body.dto.BodySeriesResponse;
import com.gymlog.body.dto.DerivedMetricResponse;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.stats.MovingAverage;
import com.gymlog.user.User;
import com.gymlog.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 身体数据 —— 录入、趋势序列、元数据。
 *
 * <h3>★ 幂等：靠业务键 (指标, 部位, 测量时刻)</h3>
 *
 * <p>这是本项目第一个**没有现成业务键可用**的写接口，
 * 也是离线队列（3.13）落地时最容易出问题的一个——体重是用户
 * 最可能反复补录、反复重传的数据。
 *
 * <p>唯一键 {@code uk_body_metric_natural} 就是业务键，
 * 写入走 UPSERT 语义，和 {@code SetRecordService.recordSet} 一样。
 *
 * <p>⚠️ <b>唯一键能生效的前提是 {@code site} 为 NOT NULL</b>——
 * MySQL 的唯一索引把 NULL 当作互不相等，site 可空的话，
 * 「同一时刻的体重」可以插进去任意多条。见 {@link BodySite} 的类注释。
 *
 * <h3>删除与「历史快照」的关系</h3>
 *
 * <p>删除一条体重记录**不会**改变任何历史训练会话的容量。
 * 会话在创建时就快照了 {@code body_weight_kg}（REQUIREMENTS 3.3 不变量 1），
 * 那是有意为之：用户三个月前那场训练的自重动作容量，
 * 必须按**当时**的体重算，不能因为今天删了一条记录就变。
 *
 * <p>用户会以为「删了体重，训练总结会变准」——**不会**。这条写进文档了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BodyService {

    /** MA7 的窗口（{@code METRICS 1.4} 主序列） */
    private static final int MA_WINDOW_DAYS = 7;

    /** 单次最多返回的原始记录数。防止「一年每天测 3 次」这种数据把响应撑爆 */
    private static final int MAX_SERIES_ROWS = 2000;

    private final BodyMetricMapper bodyMetricMapper;
    private final UserMapper userMapper;

    // ==================================================================
    // 元数据
    // ==================================================================

    /**
     * 所有身体指标的定义，供客户端生成录入表单。
     *
     * <p>返回**全部**指标（含用户从没用过的）——这正是元数据接口的意义：
     * 用户要能发现自己还没记过的指标。
     */
    public List<BodyMetricTypeResponse> metricTypes() {
        return Arrays.stream(BodyMetricType.values())
                .map(BodyMetricTypeResponse::from)
                .toList();
    }

    // ==================================================================
    // 录入
    // ==================================================================

    /**
     * 记录一次测量。**UPSERT** —— 同一 (指标, 部位, 时刻) 再传一次是覆盖，不是新增。
     *
     * <p>为什么要覆盖而不是报错：离线重试会带着同一条记录再来一次，
     * 报错会让客户端把它当成失败而继续重试。而且用户当场改主意
     * （先填 72.4，想想不对改成 72.6）再点一次保存是最自然的交互。
     */
    @Transactional
    public BodyMetricResponse record(Long userId, BodyMetricRequest request) {
        BodyMetricType type = request.metricType();
        BodySite site = request.site() == null ? BodySite.NONE : request.site();

        // 校验全部委托给枚举——量程和部位白名单只有那一份定义
        type.validateSite(site);
        type.validateValue(request.value());

        BodyMetric existing = findExisting(userId, type, site, request.measuredAt());

        BodyMetric entity = existing == null ? new BodyMetric() : existing;
        entity.setUserId(userId);
        entity.setMetricType(type);
        entity.setSite(site);
        entity.setValue(request.value());
        entity.setMeasuredAt(request.measuredAt());

        // 不支持的字段**静默忽略**而不是报错：
        // 客户端版本不一致时（老客户端多传一个字段）不该让用户录不进去
        entity.setMeasureCondition(type.acceptsCondition() ? request.condition() : null);
        entity.setNote(blankToNull(request.note()));

        if (existing == null) {
            bodyMetricMapper.insert(entity);
        } else {
            bodyMetricMapper.updateById(entity);
        }

        log.info("身体数据写入 | userId={} {} {} = {} {} | upsert={}",
                userId, type.name(), site.name(), request.value(), type.getUnit(),
                existing != null);

        return BodyMetricResponse.from(entity);
    }

    /**
     * 按业务键找已有记录。
     *
     * <p>用 {@code LambdaQueryWrapper} 而不是手写 SQL——逻辑删除、
     * 驼峰映射这些由框架保证，手写 SQL 每处都要自己记得。
     */
    private BodyMetric findExisting(Long userId, BodyMetricType type, BodySite site,
                                    LocalDateTime measuredAt) {
        return bodyMetricMapper.selectOne(new LambdaQueryWrapper<BodyMetric>()
                .eq(BodyMetric::getUserId, userId)
                .eq(BodyMetric::getMetricType, type)
                .eq(BodyMetric::getSite, site)
                .eq(BodyMetric::getMeasuredAt, measuredAt));
    }

    // ==================================================================
    // 推导指标
    // ==================================================================

    /**
     * 从自测数据推导出的指标（BMI、体脂率估算、BMR、腰高比）。
     *
     * <h3>为什么**实时算**而不是落库</h3>
     *
     * <p>派生值没有独立的测量时刻——体重一变它就该变。存下来就要处理
     * 「体重改了，派生的怎么办」，那是个没有正确答案的问题：
     * 重算会让历史值变化（违反快照语义），不重算又会让图上出现和体重对不上的数。
     *
     * <p>而实时算的成本是零：体重和腰围本来就要各查一次。
     *
     * <h3>为什么不出趋势曲线</h3>
     *
     * <p>BMI = 体重 / 身高²，身高是常数 → <b>BMI 曲线和体重曲线形状完全一样</b>。
     * 体脂率估算和 BMR 同理（年龄在短期内不变，也由体重决定）。
     * 三条曲线提供的信息和体重曲线**完全等价**，属于 {@code METRICS 0.1}
     * 说的「装饰」。所以只给当前值 + 解读。
     *
     * @param today 由 Controller 传入——Service 层允许用 now()，
     *              但显式传参让「年龄」在测试里可固定
     */
    public List<DerivedMetricResponse> derived(Long userId, LocalDate today) {
        User user = userMapper.selectById(userId);
        BigDecimal heightCm = user == null ? null : user.getHeightCm();
        Integer gender = user == null ? null : user.getGender();
        Boolean male = gender == null || gender == User.GENDER_UNSET
                ? null : gender == User.GENDER_MALE;
        Integer age = user == null || user.getBirthYear() == null
                ? null : BodyDerived.ageFrom(user.getBirthYear(), today.getYear());

        // 体重和腰围各取「最近一次」——都用 weightAsOf/selectSeries 已有的路径，
        // 不新写查询。腰围取的是**最近一次测量**，不管多久以前
        BigDecimal weight = latestValue(userId, BodyMetricType.WEIGHT, BodySite.NONE);
        BigDecimal waist = latestValue(userId, BodyMetricType.CIRCUMFERENCE, BodySite.WAIST);

        List<DerivedMetricResponse> out = new ArrayList<>(4);
        out.add(bmi(weight, heightCm));
        out.add(bodyFat(weight, heightCm, age, male));
        out.add(bmr(weight, heightCm, age, male));
        out.add(whtr(waist, heightCm));
        return out;
    }

    /** 某指标某部位的最近一次测量值。没有记录返回 null */
    private BigDecimal latestValue(Long userId, BodyMetricType type, BodySite site) {
        BodyMetric latest = bodyMetricMapper.selectOne(new LambdaQueryWrapper<BodyMetric>()
                .eq(BodyMetric::getUserId, userId)
                .eq(BodyMetric::getMetricType, type)
                .eq(BodyMetric::getSite, site)
                .orderByDesc(BodyMetric::getMeasuredAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getValue();
    }

    private DerivedMetricResponse bmi(BigDecimal weight, BigDecimal height) {
        BigDecimal v = BodyDerived.bmi(weight, height);
        return new DerivedMetricResponse("BMI", "BMI", "kg/m²", v,
                BodyDerived.bmiLabel(v),
                "中国标准：18.5–23.9 正常，24–27.9 超重，≥28 肥胖",
                "体重 ÷ 身高²",
                missingOf(height == null ? "身高" : null, weight == null ? "体重" : null));
    }

    private DerivedMetricResponse bodyFat(BigDecimal weight, BigDecimal height,
                                          Integer age, Boolean male) {
        BigDecimal v = BodyDerived.bodyFatPercent(weight, height, age, male);
        return new DerivedMetricResponse("BODY_FAT_EST", "体脂率", "%", v,
                BodyDerived.bodyFatLabel(v, male),
                "Deurenberg 公式，个体误差约 ±5 个百分点。看趋势可以，别看绝对值",
                "1.20×BMI + 0.23×年龄 − 10.8×性别 − 5.4",
                missingOf(height == null ? "身高" : null,
                        weight == null ? "体重" : null,
                        age == null ? "出生年" : null,
                        male == null ? "性别" : null));
    }

    private DerivedMetricResponse bmr(BigDecimal weight, BigDecimal height,
                                      Integer age, Boolean male) {
        BigDecimal v = BodyDerived.bmr(weight, height, age, male);
        return new DerivedMetricResponse("BMR_EST", "基础代谢率", "kcal/日", v, null,
                "躺着不动的消耗，不含任何活动。要算每日总消耗还得乘活动系数",
                "Mifflin-St Jeor 公式",
                missingOf(height == null ? "身高" : null,
                        weight == null ? "体重" : null,
                        age == null ? "出生年" : null,
                        male == null ? "性别" : null));
    }

    private DerivedMetricResponse whtr(BigDecimal waist, BigDecimal height) {
        BigDecimal v = BodyDerived.waistToHeight(waist, height);
        return new DerivedMetricResponse("WHTR", "腰高比", "", v,
                BodyDerived.waistToHeightLabel(v),
                "腰围不超过身高的一半（0.5）。比 BMI 更能反映中心性脂肪",
                "腰围 ÷ 身高",
                missingOf(height == null ? "身高" : null, waist == null ? "腰围" : null));
    }

    /** 把缺的资料拼成「身高、性别」。全都不缺时返回 null */
    private static String missingOf(String... needed) {
        List<String> missing = Arrays.stream(needed).filter(Objects::nonNull).toList();
        return missing.isEmpty() ? null : String.join("、", missing);
    }

    // ==================================================================
    // 给训练会话取快照体重
    // ==================================================================

    /**
     * 取 {@code at} 时刻**之前最近一次**体重。
     *
     * <p><b>这是 {@code workout_session.body_weight_kg} 的数据来源</b>
     * （V12 加了这一列，注释写着「Phase 4 之前恒为 NULL」——这里就是 Phase 4）。
     *
     * <h3>⚠️ 必须是「{@code measured_at <= at} 的最近一条」，不是「现在的最近一条」</h3>
     *
     * <p>{@code SessionService.create()} 的 {@code startedAt} 是**客户端传的**——
     * 离线训练时服务端不在场，用户可能两天后才把数据补传上来。
     * 那时候用「今天的体重」去算两天前那场训练的自重容量，
     * 会得出一个**当时不可能知道**的数字。
     *
     * <h3>⚠️ 只影响之后的会话，不追溯改变历史</h3>
     *
     * <p>这是快照语义（{@code REQUIREMENTS 3.3} 不变量 1），有意为之：
     * 历史容量必须按当时的体重算。
     *
     * <p>副作用要说清楚：<b>图上会出现一次台阶式跳变</b>——
     * 自重动作（引体、俯卧撑）在某个日期之前贡献 0、之后突然开始贡献容量，
     * 用户会以为是「我变强了」。所以 {@code DemoDataSeeder} 给早期会话
     * 也补了体重，让这条路径有数据可看；文档里也写了。
     *
     * @return 没有记录时返回 {@code null}——调用方要容忍，
     *         而不是拿一个默认体重顶上（{@code METRICS 4.1}「宁可不计，也不能拿假体重算」）
     */
    public BigDecimal weightAsOf(Long userId, LocalDateTime at) {
        if (at == null) {
            return null;
        }
        // 刻意**不加 @Transactional**：这是只读查询，加事务边界反而会在
        // 查询失败时把外层事务标记成 rollback-only，让「取体重失败」
        // 升级成「开训练失败」——那是本条注释下面那段容错的全部意义。
        BodyMetric latest = bodyMetricMapper.selectOne(new LambdaQueryWrapper<BodyMetric>()
                .eq(BodyMetric::getUserId, userId)
                .eq(BodyMetric::getMetricType, BodyMetricType.WEIGHT)
                .eq(BodyMetric::getSite, BodySite.NONE)
                .le(BodyMetric::getMeasuredAt, at)
                .orderByDesc(BodyMetric::getMeasuredAt)
                .last("LIMIT 1"));
        return latest == null ? null : latest.getValue();
    }

    // ==================================================================
    // 删除
    // ==================================================================

    /**
     * 删除一条记录。
     *
     * <p><b>只影响这条记录本身，不影响任何训练会话。</b>
     * 会话的 {@code body_weight_kg} 是创建时的快照（REQUIREMENTS 3.3 不变量 1），
     * 删体重**不会**让三个月前那场训练的自重容量变小或变大。
     * 用户会以为会，所以要写进文档。
     */
    @Transactional
    public void delete(Long userId, Long id) {
        BodyMetric metric = bodyMetricMapper.selectById(id);
        // 归属校验：查别人的数据一律当作不存在，不区分「不存在」和「不是你的」
        // （AC-1-1：用户 A 无法通过改 URL 里的 ID 访问用户 B 的数据）
        if (metric == null || !metric.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.BODY_METRIC_NOT_FOUND);
        }
        bodyMetricMapper.deleteById(id);
        log.info("身体数据删除 | userId={} id={} {} {}", userId, id,
                metric.getMetricType().name(), metric.getValue());
    }

    // ==================================================================
    // 原始记录列表
    // ==================================================================

    /**
     * 某指标的原始记录，**倒序**（最近的在最前）。
     *
     * <p>用途是「最近记录」列表和删除入口——用户录错了要能改，
     * 而改的第一步是找到它。倒序是因为要找的几乎总是最近那条。
     */
    public List<BodyMetricResponse> list(Long userId, BodyMetricType type, BodySite site,
                                         LocalDate from, LocalDate to, int limit) {
        LambdaQueryWrapper<BodyMetric> q = new LambdaQueryWrapper<BodyMetric>()
                .eq(BodyMetric::getUserId, userId)
                .eq(BodyMetric::getMetricType, type)
                .orderByDesc(BodyMetric::getMeasuredAt)
                // 不用 Math.clamp —— 那是 Java 21 才有的，本项目是 17
                .last("LIMIT " + Math.min(Math.max(limit, 1), 500));

        if (site != null) {
            q.eq(BodyMetric::getSite, site);
        }
        if (from != null) {
            q.ge(BodyMetric::getMeasuredAt, from.atStartOfDay());
        }
        if (to != null) {
            q.le(BodyMetric::getMeasuredAt, to.atTime(LocalTime.MAX));
        }

        return bodyMetricMapper.selectList(q).stream()
                .map(BodyMetricResponse::from)
                .toList();
    }

    // ==================================================================
    // 趋势序列
    // ==================================================================

    /**
     * 某指标（某部位）的趋势序列。
     *
     * <p><b>聚合在 Java 做，不在 SQL 做。</b>理由和 {@code SessionSummaryService}
     * 那条一样：口径是一组规则（自然日边界、先日均再窗口平均），
     * 写成 SQL 就是一层 {@code GROUP BY} 套一层窗口函数，
     * 而且会和 {@link MovingAverage} 分叉成两份实现。
     * 一个区间最多几百行，不值得把规则劈成两半。
     */
    public BodySeriesResponse series(Long userId, BodyMetricType type, BodySite site,
                                     LocalDate from, LocalDate to) {
        // 传了部位就得校验——挡住「围度 + 小腿」这种字段用错，
        // 和录入走同一份白名单（BodyMetricType.validateSite）
        if (site != BodySite.NONE) {
            type.validateSite(site);
        }

        // ★ 围度没给部位**不是错误**，返回「有哪些部位可选」。
        //
        // 第一版这里抛 60005，然后在真机上撞出一个死循环：
        // 客户端要画部位切换器，就得知道「哪些部位有数据」，
        // 而那个列表在 series 响应里——可 series 不传 site 就被拒。
        // 用户永远选不了部位，页面卡在转圈。
        //
        // 对 GET 来说，「把可选项列出来」严格优于「报错说漏了参数」：
        // 错误只告诉调用方做错了，列表告诉它该做什么。
        //
        // 60005 保留给 POST（录入时不写部位确实是错的——
        // 「围度 82cm」不说明任何事），以及别处显式的 requiresSite 检查。
        if (type.requiresSite() && site == BodySite.NONE) {
            log.debug("围度未指定部位，返回可选部位列表 | userId={}", userId);
            return BodySeriesResponse.empty(type, site, availableSites(userId, type));
        }

        List<BodyMetric> raw = bodyMetricMapper.selectSeries(
                userId, type, site, from.atStartOfDay(), to.atTime(LocalTime.MAX));

        if (raw.size() > MAX_SERIES_ROWS) {
            log.warn("身体数据序列超过上限，已截断 | userId={} {} {} rows={}",
                    userId, type.name(), site.name(), raw.size());
            raw = raw.subList(0, MAX_SERIES_ROWS);
        }

        List<MovingAverage.DailyPoint> daily = BodySeries.dailyAverage(raw);
        List<MovingAverage.MaPoint> ma = MovingAverage.rolling(daily, MA_WINDOW_DAYS);

        // ★ 总点数不够时，把每个点的 ma 也抹成 null。
        //
        // 为什么不只靠 enoughDataForMa 这个布尔量：
        // MIN_POINTS_IN_WINDOW = 2 意味着只有两天数据时，第二天的 ma **是算得出来的**。
        // 于是响应里会出现「enoughDataForMa=false，但 maPoints 里有一个非 null 的 ma」。
        // 这时客户端只要漏看了那个布尔量，就会画出一条 METRICS 1.5 明说不该画的线
        // （「数据点 < 3 天 → 不显示移动平均线」）。
        //
        // 抹掉之后，「ma == null 就不画」成了唯一需要遵守的规则，
        // 而 enoughDataForMa 退化成纯粹的文案开关（要不要提示「数据不足」）——
        // 一个是数据约束，一个是界面提示，不会再互相矛盾。
        if (!MovingAverage.enoughDataForMa(daily)) {
            ma = ma.stream()
                    .map(p -> new MovingAverage.MaPoint(p.date(), p.value(), null))
                    .toList();
        }

        return new BodySeriesResponse(
                type.name(),
                type.getLabel(),
                type.getUnit(),
                site.name(),
                site.getLabel(),
                availableSites(userId, type),
                BodySeries.toRawPoints(raw).stream()
                        .map(p -> new BodySeriesResponse.RawPoint(
                                p.measuredAt(), p.value(),
                                p.condition() == null ? null : p.condition().name(),
                                p.condition() == null ? null : p.condition().getLabel()))
                        .toList(),
                daily.stream()
                        .map(d -> new BodySeriesResponse.DailyPoint(d.date(), d.value()))
                        .toList(),
                ma.stream()
                        .map(m -> new BodySeriesResponse.MaPoint(m.date(), m.value(), m.ma()))
                        .toList(),
                MovingAverage.enoughDataForMa(daily),
                raw.isEmpty() ? null : raw.get(raw.size() - 1).getMeasuredAt(),
                BodySeries.latestValue(raw),
                reference(raw, ma),
                conditionHint(raw));
    }

    /**
     * 有数据的部位列表。
     *
     * <p>非围度指标返回空数组——它们没有部位概念，
     * 而客户端不该为了「有没有部位」再去判断一次 metricType。
     */
    private List<BodyMetricTypeResponse.SiteOption> availableSites(Long userId,
                                                                   BodyMetricType type) {
        if (!type.hasSites()) {
            return List.of();
        }
        List<String> used = bodyMetricMapper.selectUsedSiteCodes(userId, type);
        return BodySeries.usedSites(used, orderFor(type)).stream()
                .map(BodyMetricTypeResponse::siteOption)
                .toList();
    }

    /** 切换器的顺序：按枚举声明顺序（解剖学顺序），不是按数据出现顺序 */
    private List<BodySite> orderFor(BodyMetricType type) {
        return new ArrayList<>(type.getAllowedSites());
    }

    /**
     * 最新值相对**参考基准**的变化 —— 以及基准叫什么。
     *
     * <h3>⚠️ 为什么不是「距上一次测量」</h3>
     *
     * <p>第一版就是那么写的，然后在真数据上算出了**方向相反的结论**：
     * 体重从 74.45 降到 73.33，接口却返回 {@code +1.01}。
     *
     * <p>原因是它拿 9-18 的**晨起空腹**值去减 9-16 的**训练后**值——
     * 两个不同条件的读数相减，差值主要来自测量时机而不是身体变化。
     * 用户看到「+1.01」会以为涨了一公斤。
     *
     * <p>这恰恰是 {@code METRICS 1.3} 整节在防的事
     * （「日间波动 1–2kg，信噪比 1:2 到 1:4」），而且规格里本来就写了该给什么：
     *
     * <blockquote>
     *   1.4 图表规格 · 交互：点击某点显示该次测量值、测量条件、
     *   <b>相对 7 日均值的偏差</b>
     * </blockquote>
     *
     * <h3>两级基准</h3>
     *
     * <ol>
     *   <li><b>7 日均值</b>——有 MA 时优先用它。它按自然日均值再平均，
     *       已经把日内噪声和测量条件一起压掉了，是唯一适合做「我变了吗」的基准</li>
     *   <li><b>上一次<u>同条件</u>的测量</b>——没有 MA 时（数据不足 3 天、
     *       或者围度这种月度测量的指标）退化到它。
     *       <b>「同条件」是必须的</b>：不同条件相减就是上面那个 bug</li>
     * </ol>
     *
     * <p>找不到任何基准时返回 {@code null}（第一次记录、或者上一条条件不同），
     * **不是 0**：「第一次测」和「和上次一样」在界面上必须能区分。
     *
     * @return {@code (变化量, 基准名称)}；无基准时两者都为 null
     */
    private BodySeriesResponse.ChangeVsReference reference(
            List<BodyMetric> raw, List<MovingAverage.MaPoint> ma) {

        if (raw.isEmpty()) {
            return new BodySeriesResponse.ChangeVsReference(null, null);
        }
        BigDecimal latest = raw.get(raw.size() - 1).getValue();

        // ---------- 基准 1：7 日均值 ----------
        if (!ma.isEmpty()) {
            BigDecimal latestMa = ma.get(ma.size() - 1).ma();
            if (latestMa != null) {
                return new BodySeriesResponse.ChangeVsReference(
                        latest.subtract(latestMa), "7 日均值");
            }
        }

        // ---------- 基准 2：上一次同条件的测量 ----------
        MetricCondition latestCondition = raw.get(raw.size() - 1).getMeasureCondition();
        for (int i = raw.size() - 2; i >= 0; i--) {
            if (java.util.Objects.equals(raw.get(i).getMeasureCondition(), latestCondition)) {
                return new BodySeriesResponse.ChangeVsReference(
                        latest.subtract(raw.get(i).getValue()),
                        latestCondition == null ? "上次测量" : "上次" + latestCondition.getLabel());
            }
        }
        return new BodySeriesResponse.ChangeVsReference(null, null);
    }

    /**
     * 测量条件混杂时的提示文案（{@code METRICS 1.5}）。
     *
     * <p>「不阻止，但在图上区分标记」——区分形状是客户端的事，
     * 而**告诉用户为什么曲线在锯齿**是这里的事。
     *
     * <p>不提示的情况：只有一种条件，或者压根没填条件。
     * 后者的理由和别处一致——「没填」不等于「填了其他」。
     */
    private String conditionHint(List<BodyMetric> raw) {
        Set<MetricCondition> conditions = new LinkedHashSet<>();
        for (BodyMetric m : raw) {
            if (m.getMeasureCondition() != null) {
                conditions.add(m.getMeasureCondition());
            }
        }
        if (conditions.size() < 2) {
            return null;
        }
        boolean hasPostWorkout = conditions.contains(MetricCondition.POST_WORKOUT);
        return hasPostWorkout
                ? "这段时间里既有晨起也有训练后测量，训练后读数会偏低——"
                  + "曲线上的锯齿有一部分来自测量条件，不是身体的变化"
                : "这段时间里的测量条件不统一，曲线上的锯齿有一部分来自测量条件";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
