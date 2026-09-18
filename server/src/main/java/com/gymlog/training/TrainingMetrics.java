package com.gymlog.training;

import com.gymlog.exercise.MetricType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * 训练指标的**唯一**计算入口 —— 纯函数，无依赖无状态。
 *
 * <h3>为什么必须只有一个实现</h3>
 *
 * <p>口径一旦分叉，两个地方算出的容量会不一样，而且**都不会报错**。
 * 用户看到的是「训练总结说 3200kg，周报图说 3050kg」，
 * 然后开始怀疑整个 App 的数据。
 *
 * <p>口径定义在 {@code docs/METRICS.md}，这里逐条实现，注释里标出处。
 * 改口径时**只改这一个文件**。
 *
 * <h3>容量口径（METRICS 4.1）</h3>
 * <pre>
 *   仅正式组（set_type ≠ WARMUP）
 *   WEIGHT_REPS        Σ(重量 × 次数)
 *   REPS_ONLY（自重）   Σ(体重 × bw_factor × 次数)
 *   DURATION 类        不计入容量（时长折算不成重量×次数）
 * </pre>
 */
public final class TrainingMetrics {

    /** e1RM 只对 weight_reps 有意义——自重和时长类动作没有「1RM」这个概念 */
    private static final int E1RM_MAX_REPS = 12;

    private TrainingMetrics() {
    }

    // ==================================================================
    // 组数
    // ==================================================================

    /**
     * 这一组算不算「正式组」。
     *
     * <p>热身组不计入容量、组数、总次数（{@code METRICS 4.1}）。
     *
     * <h4>为什么单独抽出来</h4>
     *
     * <p>这条规则原本**内联散落在三个地方**（都写 {@code setType != WARMUP}）：
     * {@code SessionSummaryService} 的总结主循环、与上次对比、历史列表统计。
     * 三处各写一遍，看起来毫无风险——直到要加第四个地方（Phase 4 的周组数聚合）。
     *
     * <p>「一个公式一处实现」这个类存在的全部理由，就是防这种缓慢分叉：
     * 某天有人给热身组开了个例外（比如「热身也算半组」），
     * 他只会在自己改的那一处加，另外三处不动，然后四个数字互相对不上，
     * **而且都不会报错**。
     */
    public static boolean isWorkingSet(SetType setType) {
        return setType != SetType.WARMUP;
    }

    /** 一组记录里的正式组数。规则见 {@link #isWorkingSet}。 */
    public static int countWorkingSets(Collection<SetRecord> records) {
        return (int) records.stream()
                .filter(r -> isWorkingSet(r.getSetType()))
                .count();
    }

    // ==================================================================
    // 容量
    // ==================================================================

    /**
     * 单组的训练容量（kg）。
     *
     * <p>返回 {@link BigDecimal#ZERO} 表示这一组不计入容量，
     * 而不是 null——调用方可以直接累加，不用到处判空。
     *
     * @param record   实际记录
     * @param exercise 动作快照（提供计量类型和 bw_factor）
     * @param session  会话（提供快照的体重）
     */
    public static BigDecimal setVolume(SetRecord record,
                                       SessionExercise exercise,
                                       WorkoutSession session) {

        // ---------- 热身组不计入（全局口径）----------
        //
        // 热身组往往次数多、重量轻，而且因人因日而异，
        // 是最不稳定的一块。混进去会让所有趋势失真。
        if (record.getSetType() == SetType.WARMUP) {
            return BigDecimal.ZERO;
        }

        if (record.getReps() == null || record.getReps() <= 0) {
            return BigDecimal.ZERO;
        }

        MetricType metric = exercise.getMetricType();
        if (metric == null) {
            return BigDecimal.ZERO;
        }

        return switch (metric) {
            case WEIGHT_REPS -> nullToZero(record.getWeight())
                    .multiply(BigDecimal.valueOf(record.getReps()));

            case REPS_ONLY -> {
                // 自重动作：容量 = 体重 × bw_factor × 次数
                //
                // ⚠️ 这两个值都来自**快照**，不是现查动作库和身体数据。
                // bw_factor 管理员可以改、体重每周在变，
                // 现查的话历史容量会追溯性地变化。
                BigDecimal bodyWeight = session.getBodyWeightKg();
                BigDecimal factor = exercise.getBwFactor();

                // METRICS 4.1 边界情况：体重未记录时该次不计入容量。
                //
                // 宁可不计，也不能拿一个假体重去算——
                // 算出来的数字会被当成真的，而缺口至少是显式的。
                if (bodyWeight == null || factor == null) {
                    yield BigDecimal.ZERO;
                }
                yield bodyWeight.multiply(factor)
                        .multiply(BigDecimal.valueOf(record.getReps()));
            }

            // 时长类和距离类无法折算成「重量×次数」，
            // 单独统计总时长/总距离，不进容量（METRICS 4.1）
            case DURATION, DISTANCE_DURATION -> BigDecimal.ZERO;
        };
    }

    /** 单组容量保留 2 位小数，避免累加出一串浮点尾巴 */
    public static BigDecimal round(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    // ==================================================================
    // 估算 1RM
    // ==================================================================

    /**
     * 估算 1RM（Epley 公式）：{@code w × (1 + r/30)}。
     *
     * <p><b>超过 12 次返回 null。</b>
     *
     * <p>Epley 在高次数区间**严重高估**——20 次力竭组的真实 1RM
     * 远低于公式给出的值。不排除的话，曲线会被高次数组虚抬，
     * 用户以为自己变强了。
     *
     * <p>返回 null 而不是 0：调用方要能区分「这组不参与计算」
     * 和「这组算出 0」——后者会污染 MAX。
     *
     * @return 估算 1RM（kg），或 null 表示该组不参与
     */
    public static BigDecimal e1rm(BigDecimal weight, Integer reps) {
        if (weight == null || reps == null || reps <= 0) {
            return null;
        }
        if (reps > E1RM_MAX_REPS) {
            return null;
        }
        // 30 用 BigDecimal 而不是 30.0：保持精确，避免二进制浮点误差
        BigDecimal factor = BigDecimal.ONE.add(
                BigDecimal.valueOf(reps).divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP));
        return weight.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 一次训练中某个动作的代表 e1RM —— **取最佳组，不取平均**。
     *
     * <p>取平均的话，递减组、疲劳组、热身残留会把值拉低，
     * 曲线会**低估**真实力量，而且受当天组数安排影响——
     * 多做一个轻组就会让曲线下降，这不合理。
     *
     * <p>最佳组最接近当天的真实能力上限。
     */
    public static BigDecimal bestE1rm(SetRecord record, SessionExercise exercise) {
        // 自重和时长类动作没有 e1RM 概念（METRICS 3.6）
        if (exercise.getMetricType() != MetricType.WEIGHT_REPS) {
            return null;
        }
        if (record.getSetType() == SetType.WARMUP) {
            return null;
        }
        return e1rm(record.getWeight(), record.getReps());
    }

    // ==================================================================
    // 小工具
    // ==================================================================

    private static BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
