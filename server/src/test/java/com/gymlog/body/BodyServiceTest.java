package com.gymlog.body;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.body.dto.BodyMetricRequest;
import com.gymlog.body.dto.BodySeriesResponse;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 身体数据服务 —— 集成测试，走真实 MySQL。
 *
 * <p><b>这个类里最重要的不是业务逻辑，是幂等。</b>
 * 身体数据是离线优先架构下**第一个没有现成业务键**的写接口，
 * 而幂等键能不能生效，取决于一个很容易被改错的东西：
 * {@code body_metric.site} 是 {@code NOT NULL}。
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class BodyServiceTest {

    private static final Long USER = 1L;
    private static final Long OTHER_USER = 2L;

    private static final LocalDateTime T1 = LocalDateTime.of(2026, 9, 1, 7, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, 9, 2, 7, 0);

    @Autowired private BodyService bodyService;
    @Autowired private BodyMetricMapper bodyMetricMapper;

    private static BodyMetricRequest weight(String value, LocalDateTime at) {
        return new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                new BigDecimal(value), at, MetricCondition.FASTED, null);
    }

    private long countWeight() {
        return bodyMetricMapper.selectCount(new LambdaQueryWrapper<BodyMetric>()
                .eq(BodyMetric::getUserId, USER)
                .eq(BodyMetric::getMetricType, BodyMetricType.WEIGHT));
    }

    // ==================================================================

    @Nested
    @DisplayName("幂等 —— AC-5-1 在身体数据上的落地")
    class Idempotency {

        @Test
        @DisplayName("★ 同一 (指标, 部位, 时刻) 重复提交 3 次，库里只有 1 条")
        void repeatedUploadProducesOneRow() {
            long before = countWeight();

            for (int i = 0; i < 3; i++) {
                bodyService.record(USER, weight("72.4", T1));
            }

            assertThat(countWeight()).as("重复上传 3 次只应产生 1 条").isEqualTo(before + 1);
        }

        @Test
        @DisplayName("★ 重复提交是覆盖而不是新增 —— 值以最后一次为准")
        void lastWriteWins() {
            bodyService.record(USER, weight("72.4", T1));
            bodyService.record(USER, weight("72.9", T1));

            var rows = bodyMetricMapper.selectList(new LambdaQueryWrapper<BodyMetric>()
                    .eq(BodyMetric::getUserId, USER)
                    .eq(BodyMetric::getMetricType, BodyMetricType.WEIGHT)
                    .eq(BodyMetric::getMeasuredAt, T1));

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getValue()).isEqualByComparingTo("72.90");
        }

        @Test
        @DisplayName("★ 唯一键对「没有部位」的指标真的生效 —— 这正是 site 必须 NOT NULL 的原因")
        void uniqueKeyWorksForMetricsWithoutSite() {
            // ⚠️ 这条测试守的是一个**数据库层**的陷阱，Java 层看不出来：
            //
            // MySQL 的 UNIQUE 索引把 NULL 当作**互不相等**。如果 site 是可空列，
            // 那么 (user, WEIGHT, NULL, T1) 可以插进去任意多条——
            // 而上层代码（record 里的 findExisting 查询）逻辑完全正确，
            // 单测全绿，只有真正并发或重放时才会冒出重复行。
            //
            // 所以这条测试**绕过 Service 直接写库**，验的是约束本身。
            BodyMetric first = new BodyMetric();
            first.setUserId(USER);
            first.setMetricType(BodyMetricType.WEIGHT);
            first.setSite(BodySite.NONE);
            first.setValue(new BigDecimal("72.4"));
            first.setMeasuredAt(T1);
            bodyMetricMapper.insert(first);

            BodyMetric duplicate = new BodyMetric();
            duplicate.setUserId(USER);
            duplicate.setMetricType(BodyMetricType.WEIGHT);
            duplicate.setSite(BodySite.NONE);
            duplicate.setValue(new BigDecimal("99.9"));
            duplicate.setMeasuredAt(T1);

            assertThatThrownBy(() -> bodyMetricMapper.insert(duplicate))
                    .as("site 为 NONE 时唯一键仍然必须挡住第二条。"
                        + "如果这条变绿了（不再抛异常），说明 site 被改成了可空列——"
                        + "去检查 V15 迁移和 BodySite 的注释")
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        }

        @Test
        @DisplayName("不同部位的围度是两条独立记录")
        void differentSitesAreDifferentRecords() {
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.WAIST, new BigDecimal("82.0"), T1, null, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.HIP, new BigDecimal("95.0"), T1, null, null));

            var rows = bodyMetricMapper.selectList(new LambdaQueryWrapper<BodyMetric>()
                    .eq(BodyMetric::getUserId, USER)
                    .eq(BodyMetric::getMetricType, BodyMetricType.CIRCUMFERENCE)
                    .eq(BodyMetric::getMeasuredAt, T1));

            assertThat(rows).hasSize(2);
        }

        @Test
        @DisplayName("不同时刻的同一指标是两条记录")
        void differentTimestampsAreDifferentRecords() {
            bodyService.record(USER, weight("72.4", T1));
            bodyService.record(USER, weight("72.1", T2));

            assertThat(bodyMetricMapper.selectList(new LambdaQueryWrapper<BodyMetric>()
                    .eq(BodyMetric::getUserId, USER)
                    .eq(BodyMetric::getMetricType, BodyMetricType.WEIGHT)
                    .in(BodyMetric::getMeasuredAt, T1, T2))).hasSize(2);
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("校验")
    class Validation {

        @Test
        @DisplayName("量程越界被拒，且错误码是 60002")
        void valueOutOfRange() {
            assertThatThrownBy(() -> bodyService.record(USER, weight("500", T1)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_VALUE_OUT_OF_RANGE);
        }

        @Test
        @DisplayName("★ 体重带部位被拒 —— 挡住的是「字段用错」，不是「值不合法」")
        void siteNotAllowedForWeight() {
            assertThatThrownBy(() -> bodyService.record(USER, new BodyMetricRequest(
                    BodyMetricType.WEIGHT, BodySite.WAIST, new BigDecimal("72.4"),
                    T1, null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_SITE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("围度不给部位被拒（60005）—— 「围度 82cm」不说明任何事")
        void circumferenceRequiresSite() {
            assertThatThrownBy(() -> bodyService.record(USER, new BodyMetricRequest(
                    BodyMetricType.CIRCUMFERENCE, null, new BigDecimal("82.0"),
                    T1, null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_SITE_REQUIRED);
        }

        @Test
        @DisplayName("不支持测量条件的指标，传了也被静默忽略而不是报错")
        void unsupportedFieldsIgnored() {
            // 主观状态类指标不支持 condition（只有体重、静息心率这类对条件敏感的才支持）。
            // 老客户端多传一个字段不该让用户录不进去。
            var saved = bodyService.record(USER, new BodyMetricRequest(
                    BodyMetricType.SLEEP_QUALITY, null, new BigDecimal("4"),
                    T1, MetricCondition.FASTED, "备注"));

            assertThat(saved.condition()).as("睡眠质量不支持测量条件，应被忽略").isNull();
            assertThat(saved.note()).isEqualTo("备注");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("趋势序列")
    class Series {

        @Test
        @DisplayName("★ 一天测三次 → raw 三个点、daily 一个点、且 daily 是均值")
        void dailyAveragingFlowsThrough() {
            bodyService.record(USER, weight("72.0", LocalDateTime.of(2026, 9, 1, 7, 0)));
            bodyService.record(USER, weight("73.0", LocalDateTime.of(2026, 9, 1, 12, 0)));
            bodyService.record(USER, weight("71.0", LocalDateTime.of(2026, 9, 1, 21, 0)));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

            assertThat(s.rawPoints()).hasSize(3);
            assertThat(s.dailyPoints()).hasSize(1);
            assertThat(s.dailyPoints().get(0).value()).isEqualByComparingTo("72.00");
        }

        @Test
        @DisplayName("★ 数据点不足 3 天 → 所有 ma 都是 null，客户端不可能误画（AC-7-2）")
        void notEnoughDataForMa() {
            bodyService.record(USER, weight("72.0", T1));
            bodyService.record(USER, weight("72.1", T2));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

            assertThat(s.dailyPoints()).hasSize(2);
            assertThat(s.enoughDataForMa()).isFalse();

            // ⚠️ 这条断言不是废话，虽然「2 个点、窗口内 2 个点」会让
            // MovingAverage 算出第二天的 ma。服务端刻意把它抹成 null——
            // 否则客户端漏看 enoughDataForMa 就会画出一条 METRICS 1.5
            // 明说不该画的线。见 BodySeriesResponse 的类注释。
            assertThat(s.maPoints()).hasSize(2);
            assertThat(s.maPoints()).allSatisfy(p -> assertThat(p.ma()).isNull());
        }

        @Test
        @DisplayName("★ 三天数据 → 有 MA，且等于三天的均值（窗口内点数 ≥ 2）")
        void maComputedAcrossDays() {
            bodyService.record(USER, weight("72.0", LocalDateTime.of(2026, 9, 1, 7, 0)));
            bodyService.record(USER, weight("73.0", LocalDateTime.of(2026, 9, 2, 7, 0)));
            bodyService.record(USER, weight("74.0", LocalDateTime.of(2026, 9, 3, 7, 0)));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

            assertThat(s.enoughDataForMa()).isTrue();
            assertThat(s.maPoints()).hasSize(3);
            // 第一天窗口内只有 1 个点 → 不可用（一个点的「平均」没压掉任何噪声）
            assertThat(s.maPoints().get(0).ma()).isNull();
            assertThat(s.maPoints().get(1).ma()).isEqualByComparingTo("72.50");
            assertThat(s.maPoints().get(2).ma()).isEqualByComparingTo("73.00");
        }

        @Test
        @DisplayName("★ 基准优先取 7 日均值，而不是「上一次测量」")
        void changeUsesMaAsReference() {
            // METRICS 1.4 明确要求显示「相对 7 日均值的偏差」。
            // 用「距上次测量」的话，日间 1–2kg 的噪声会直接变成用户看到的数字——
            // 而那正是 METRICS 1.3 整节在防的事。
            bodyService.record(USER, weight("72.0", LocalDateTime.of(2026, 9, 1, 7, 0)));
            bodyService.record(USER, weight("74.0", LocalDateTime.of(2026, 9, 2, 7, 0)));
            bodyService.record(USER, weight("73.0", LocalDateTime.of(2026, 9, 3, 7, 0)));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3));

            // 7 日均值 = (72+74+73)/3 = 73.00，最新值 73.00 → 偏差 0
            // 若按「距上次测量」会得到 73-74 = -1.00，是另一个数
            assertThat(s.changeVsReference().referenceLabel()).isEqualTo("7 日均值");
            assertThat(s.changeVsReference().value()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("★ 没有 MA 时退化到「上一次同条件的测量」，绝不跨条件相减")
        void changeFallsBackToSameConditionOnly() {
            // 这就是真机上抓到的 bug：体重明明降了，接口返回 +1.01，
            // 因为它拿晨起空腹值去减了训练后值。
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("75.0"), LocalDateTime.of(2026, 9, 1, 7, 0),
                    MetricCondition.FASTED, null));
            // 训练后：读数天然低 1kg 以上，但这不是「瘦了」
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("73.5"), LocalDateTime.of(2026, 9, 1, 21, 30),
                    MetricCondition.POST_WORKOUT, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("74.6"), LocalDateTime.of(2026, 9, 2, 7, 0),
                    MetricCondition.FASTED, null));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

            // 最新是 9-2 晨起 74.6，基准应是 9-1 晨起 75.0（跳过 9-1 训练后的 73.5）
            assertThat(s.changeVsReference().referenceLabel()).isEqualTo("上次晨起空腹");
            assertThat(s.changeVsReference().value()).isEqualByComparingTo("-0.40");
        }

        @Test
        @DisplayName("没有任何基准时 value 和 label 都是 null，不是 0")
        void noReference() {
            bodyService.record(USER, weight("72.0", T1));
            BodySeriesResponse one = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

            assertThat(one.changeVsReference().value()).isNull();
            assertThat(one.changeVsReference().referenceLabel()).isNull();
        }

        @Test
        @DisplayName("条件不同且没有 MA 时不给基准 —— 宁可没有，也不给一个误导的数")
        void noReferenceWhenConditionsDiffer() {
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("75.0"), LocalDateTime.of(2026, 9, 1, 7, 0),
                    MetricCondition.FASTED, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("73.5"), LocalDateTime.of(2026, 9, 1, 21, 30),
                    MetricCondition.POST_WORKOUT, null));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

            assertThat(s.changeVsReference().value()).isNull();
        }

        @Test
        @DisplayName("围度是月度数据、没有 MA，退化到「上次测量」")
        void circumferenceUsesPreviousMeasurement() {
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.WAIST, new BigDecimal("86.0"),
                    LocalDateTime.of(2026, 9, 1, 7, 0), null, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.WAIST, new BigDecimal("84.5"),
                    LocalDateTime.of(2026, 10, 1, 7, 0), null, null));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.CIRCUMFERENCE,
                    BodySite.WAIST, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));

            assertThat(s.changeVsReference().referenceLabel()).isEqualTo("上次测量");
            assertThat(s.changeVsReference().value()).isEqualByComparingTo("-1.50");
        }

        @Test
        @DisplayName("★ 测量条件混杂时给出提示（METRICS 1.5「不阻止但要说明」）")
        void conditionHintWhenMixed() {
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("72.0"), T1, MetricCondition.FASTED, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("73.5"), T2, MetricCondition.POST_WORKOUT, null));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

            assertThat(s.conditionHint()).isNotNull().contains("训练后");
        }

        @Test
        @DisplayName("条件统一时不提示")
        void noHintWhenConsistent() {
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("72.0"), T1, MetricCondition.FASTED, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.WEIGHT, null,
                    new BigDecimal("72.1"), T2, MetricCondition.FASTED, null));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

            assertThat(s.conditionHint()).isNull();
        }

        @Test
        @DisplayName("★ 围度不给部位返回「有哪些部位可选」，不是 60005 —— 否则客户端没法画切换器")
        void circumferenceWithoutSiteReturnsAvailableSites() {
            // 第一版这里抛 60005，真机上撞出死循环：
            // 客户端要画部位切换器就得知道哪些部位有数据，而那个列表在 series 响应里，
            // 可 series 不传 site 就被拒——用户永远选不了部位。
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.WAIST, new BigDecimal("86.0"), T1, null, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.NECK, new BigDecimal("38.0"), T1, null, null));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.CIRCUMFERENCE,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

            assertThat(s.rawPoints()).isEmpty();
            assertThat(s.availableSites()).extracting(
                            com.gymlog.body.dto.BodyMetricTypeResponse.SiteOption::site)
                    .containsExactly("NECK", "WAIST");
        }

        @Test
        @DisplayName("围度 + 不属于围度的部位仍然报 60006（校验没有因为上面那条被放松）")
        void circumferenceWithWrongSiteStillRejected() {
            assertThatThrownBy(() -> bodyService.series(USER, BodyMetricType.CIRCUMFERENCE,
                    BodySite.CORE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_SITE_NOT_ALLOWED);
        }

        @Test
        @DisplayName("录入时围度不给部位仍然报 60005 —— 那条规则只对 GET 放宽")
        void recordStillRequiresSite() {
            assertThatThrownBy(() -> bodyService.record(USER, new BodyMetricRequest(
                    BodyMetricType.CIRCUMFERENCE, null, new BigDecimal("86.0"),
                    T1, null, null)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_SITE_REQUIRED);
        }

        @Test
        @DisplayName("★ availableSites 只列出有数据的部位，且按解剖学顺序（METRICS 2.5）")
        void availableSitesOnlyUsedOnes() {
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.LEFT_CALF, new BigDecimal("38.0"), T1, null, null));
            bodyService.record(USER, new BodyMetricRequest(BodyMetricType.CIRCUMFERENCE,
                    BodySite.NECK, new BigDecimal("38.5"), T1, null, null));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.CIRCUMFERENCE,
                    BodySite.NECK, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));

            // 只测了两处，就不该列出 12 个（11 个点进去是空图）
            assertThat(s.availableSites()).extracting(
                            com.gymlog.body.dto.BodyMetricTypeResponse.SiteOption::site)
                    .containsExactly("NECK", "LEFT_CALF");
        }

        @Test
        @DisplayName("没有部位概念的指标，availableSites 是空数组")
        void noSitesForWeight() {
            bodyService.record(USER, weight("72.0", T1));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

            assertThat(s.availableSites()).isEmpty();
        }

        @Test
        @DisplayName("没有数据时返回空序列，不报错")
        void emptySeries() {
            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.STRESS_LEVEL,
                    BodySite.NONE, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 31));

            assertThat(s.rawPoints()).isEmpty();
            assertThat(s.dailyPoints()).isEmpty();
            assertThat(s.maPoints()).isEmpty();
            assertThat(s.enoughDataForMa()).isFalse();
            assertThat(s.latestValue()).isNull();
            assertThat(s.latestAt()).isNull();
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("归属与删除")
    class Ownership {

        @Test
        @DisplayName("★ 删别人的记录当作「不存在」（AC-1-1）")
        void cannotDeleteOthersRecord() {
            var saved = bodyService.record(USER, weight("72.0", T1));

            assertThatThrownBy(() -> bodyService.delete(OTHER_USER, saved.id()))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.BODY_METRIC_NOT_FOUND);

            // 而且真的没被删掉
            assertThat(bodyMetricMapper.selectById(saved.id())).isNotNull();
        }

        @Test
        @DisplayName("删除自己的记录成功")
        void deleteOwnRecord() {
            var saved = bodyService.record(USER, weight("72.0", T1));
            bodyService.delete(USER, saved.id());

            assertThat(bodyMetricMapper.selectById(saved.id())).isNull();
        }

        @Test
        @DisplayName("系列查询只返回自己的数据")
        void seriesIsScopedToUser() {
            bodyService.record(USER, weight("72.0", T1));
            bodyService.record(OTHER_USER, weight("88.0", T1));

            BodySeriesResponse s = bodyService.series(USER, BodyMetricType.WEIGHT,
                    BodySite.NONE, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1));

            assertThat(s.rawPoints()).hasSize(1);
            assertThat(s.latestValue()).isEqualByComparingTo("72.00");
        }
    }

    // ==================================================================

    @Nested
    @DisplayName("给训练会话取快照体重")
    class WeightAsOf {

        @Test
        @DisplayName("★ 取的是「那一刻之前最近的一次」，不是「现在的最近一次」")
        void takesLatestBeforeNotLatestOverall() {
            // 这条是离线补传场景的核心：
            // 用户 9-10 补传一场 9-1 的训练，那时候 9-5 的体重还不存在。
            // 用「现在的最近一次」会算出当时不可能知道的容量。
            bodyService.record(USER, weight("72.0", LocalDateTime.of(2026, 9, 1, 7, 0)));
            bodyService.record(USER, weight("75.0", LocalDateTime.of(2026, 9, 10, 7, 0)));

            BigDecimal asOfSep2 = bodyService.weightAsOf(USER,
                    LocalDateTime.of(2026, 9, 2, 20, 0));

            assertThat(asOfSep2).isEqualByComparingTo("72.00");
        }

        @Test
        @DisplayName("那一刻之前没有记录时返回 null，不是拿未来的体重顶上")
        void nullWhenNoPriorRecord() {
            bodyService.record(USER, weight("75.0", LocalDateTime.of(2026, 9, 10, 7, 0)));

            assertThat(bodyService.weightAsOf(USER, LocalDateTime.of(2026, 9, 1, 7, 0)))
                    .isNull();
        }

        @Test
        @DisplayName("恰好同一时刻算「之前」（<=，不是 <）")
        void boundaryIsInclusive() {
            bodyService.record(USER, weight("72.0", LocalDateTime.of(2026, 9, 1, 7, 0)));

            assertThat(bodyService.weightAsOf(USER, LocalDateTime.of(2026, 9, 1, 7, 0)))
                    .isEqualByComparingTo("72.00");
        }

        @Test
        @DisplayName("不看别人的体重")
        void scopedToUser() {
            bodyService.record(OTHER_USER, weight("88.0", T1));

            assertThat(bodyService.weightAsOf(USER, T2)).isNull();
        }
    }
}
