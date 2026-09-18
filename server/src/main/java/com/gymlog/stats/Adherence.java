package com.gymlog.stats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;

/**
 * 计划执行度 —— 纯函数，无依赖无状态。
 *
 * <h3>为什么「符合率」和「出勤率」必须拆开</h3>
 *
 * <p>{@code METRICS 6.2}：用户临场调整是**正常的训练行为**，不是失败——
 * 状态差减了 10kg、器械被占了换个动作、感觉还有力气多做一组。
 * 合成一个指标会两边不讨好：算进「完成」掩盖了执行偏差，
 * 算进「未完成」又惩罚了合理的临场判断。
 *
 * <table>
 *   <tr><td>符合率</td><td>{@code Σmin(实际, 计划) / Σ计划}</td><td>我<b>按计划</b>练了吗？</td></tr>
 * </table>
 *
 * <h3>⚠️ 出勤率（{@code METRICS 6.1}）本类不实现，且当前 schema 下实现不了</h3>
 *
 * <p>出勤率 = {@code 完成场次 / 计划场次}，而**分母算不出来**：
 *
 * <ul>
 *   <li>计划频率存在 {@code week_template.sessions_per_week}</li>
 *   <li>而 {@code ProgramService} 的结构编辑是**全量替换**（删了重建）→ 没有历史</li>
 *   <li>{@code workout_session} 快照了 {@code week_number} / {@code is_deload}，
 *       <b>唯独没有快照 {@code sessions_per_week}</b></li>
 *   <li>最要命的是：<b>没有训练的那一周没有任何会话行</b>——
 *       而那正是出勤率最关心的周</li>
 * </ul>
 *
 * <p>于是「按当周生效的计划频率判定」（{@code METRICS 5.5}）无从谈起：
 * 用户把第 5 周频率从 4 改成 3，第 5 周的达标判定会**追溯性地改变**。
 *
 * <p>要做对得给周模板做版本化——那是 Phase 8 的活。现阶段显式不做，
 * 并且在 {@code REQUIREMENTS} 里标注理由，比返回一个会变的数字诚实。
 */
public final class Adherence {

    private Adherence() {
    }

    /** 一个「计划 vs 实际」的对比单元。粒度由调用方决定（组数 / 次数）。 */
    public record PlannedActual(int planned, int actual) {
    }

    /**
     * 符合率（0–100）。{@code METRICS 6.1}。
     *
     * <p><b>分子必须 clamp</b>（{@code AC-7-5}）：「用户多做了一组，实际 &gt; 计划。
     * 如果不 clamp，符合率会超过 100%，指标失去意义。」
     *
     * <p>注意 clamp 是**逐单元**做的（{@code Σmin(实际ᵢ, 计划ᵢ)}），
     * 不是先求和再 clamp。两者不等价：某动作多做 3 组、另一动作少做 3 组，
     * 先求和会得到 100%，逐单元则是「各扣各的」——后者才反映真实执行度。
     *
     * @return 计划总量为 0 时返回 {@code null}（{@code METRICS 6.5}：
     *         「计划场次 = 0（未设计划）→ 不计算」）。是 null 不是 0——
     *         「没计划」和「完成度为零」是完全不同的两件事
     */
    public static BigDecimal complianceRate(Collection<PlannedActual> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return null;
        }
        long plannedTotal = 0;
        long clampedTotal = 0;
        for (PlannedActual p : pairs) {
            if (p.planned() <= 0) {
                continue;
            }
            plannedTotal += p.planned();
            clampedTotal += Math.min(p.actual(), p.planned());
        }
        if (plannedTotal == 0) {
            return null;
        }
        return BigDecimal.valueOf(clampedTotal)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(plannedTotal), 1, RoundingMode.HALF_UP);
    }

    /**
     * 把一组「计划 vs 实际」打成 {@code METRICS 6.3} 的偏差标签。
     *
     * <p>标签供用户回看，是「下钻」视图的数据来源。注意 {@code METRICS 6.4}
     * 明确要求「**不用红绿二元判断**」——「修改」是正常的训练决策，
     * 渲染成错误会让用户不敢按状态调整。
     */
    public enum DeviationTag {
        /** 动作、组数、次数全部匹配 */
        AS_PLANNED("按计划"),
        /** 动作匹配，但组数 / 次数 / 重量有调整 */
        MODIFIED("修改"),
        /** 存在 exercise_id 不匹配的动作（换动作了） */
        SUBSTITUTED("替换"),
        /** 非计划内会话（临时训练） */
        AD_HOC("临时");

        private final String label;

        DeviationTag(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 给一场训练打标签。判定优先级：临时 &gt; 替换 &gt; 修改 &gt; 按计划。
     *
     * <p>优先级不能反——一场既有替换又有微调的训练，用户最想知道的
     * 是「换动作了」，而不是「重量差 2.5kg」。
     */
    public static DeviationTag classify(boolean adHoc,
                                        boolean hasSubstitution,
                                        List<PlannedActual> pairs) {
        if (adHoc) {
            return DeviationTag.AD_HOC;
        }
        if (hasSubstitution) {
            return DeviationTag.SUBSTITUTED;
        }
        if (pairs != null) {
            for (PlannedActual p : pairs) {
                if (p.actual() != p.planned()) {
                    return DeviationTag.MODIFIED;
                }
            }
        }
        return DeviationTag.AS_PLANNED;
    }
}
