package com.gymlog.program;

import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.program.dto.ExpandedWorkout;
import com.gymlog.training.SetType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 周期化展开算法 —— 把「第 5 周 +5%」展开成每一组的具体目标。
 *
 * <h3>为什么这是一个「纯函数」</h3>
 *
 * <p>整个类**没有依赖、没有状态、方法全静态**：
 * <pre>
 *   输入：计划结构 + 周修饰 + 训练日 + 处方动作 + 逐组处方 + 动作信息
 *   输出：展开后的训练内容
 * </pre>
 *
 * <p><b>这不是为了「看起来优雅」，而是有三个实际好处</b>：
 *
 * <ol>
 *   <li><b>能直接单测，不用起 Spring 容器。</b>
 *       展开逻辑有大量边界情况（周修饰为负、组数调整后归零、
 *       逐组处方缺失…），每个都该有测试。如果需要启动数据库和容器
 *       才能测一个，测试就会写得很慢很少。</li>
 *
 *   <li><b>能被两处复用。</b>
 *       「今天练什么」的预览要用它，创建训练会话时的快照也要用它。
 *       如果里面混了数据库查询或用户上下文，两处就没法共用一份逻辑。</li>
 *   <li><b>行为可预测。</b>同样的输入永远得到同样的输出——
 *       没有隐藏的「取决于当前用户是谁」或「取决于数据库里有什么」。</li>
 * </ol>
 *
 * <p><b>怎么保证它一直是纯的</b>：把类和它依赖的数据类型放在
 * 不同的包/类里还不够，关键是**不注入任何 Bean**。
 * 这个类的构造器是私有的，没有字段，没有 {@code @Autowired}——
 * 想在方法里查数据库，得先加一个字段，而加字段这个动作本身就显眼了。
 *
 * <p>（加载数据、组装参数是 {@code ProgramExpansionService} 的职责。）
 */
public final class ProgramExpander {

    /** 展开后每组至少 1 组——周修饰的 setAdjust 可能把它减到 0 或负数 */
    private static final int MIN_SETS = 1;

    /** 单个动作最多 20 组。超过这个数多半是数据错了，不是真的要练 25 组 */
    private static final int MAX_SETS = 20;

    /** 组间休息的默认值，当动作级和逐组都没指定时使用 */
    private static final int DEFAULT_REST_SEC = 90;

    /**
     * 最小有效重量。
     *
     * <p>周修饰是百分比乘法，极端情况下（基准很小 + 大幅 deload）
     * 可能算出接近 0 甚至为负的值。
     *
     * <p>0.5kg 是最小的现实配重（多数哑铃的递增单位是 1kg 或 2.5kg）。
     * 与其让界面显示「-3.2kg」这种明显错误的值，不如夹到这个下限。
     */
    private static final BigDecimal MIN_WEIGHT = new BigDecimal("0.5");

    /** 工具类，不实例化 */
    private ProgramExpander() {
    }

    /**
     * 执行展开。
     *
     * @param program      计划（仅用于填充输出里的名称，不参与计算）
     * @param week         指定周的修饰。为 null 表示按基准值展开（无调整）
     * @param day          训练日
     * @param prescriptions 该训练日的处方动作，**已排序**
     * @param setsByExercise 逐组处方，key 是 prescribedExerciseId
     * @param exercisesById  动作信息，key 是 exerciseId
     */
    public static ExpandedWorkout expand(Program program,
                                         WeekTemplate week,
                                         DayTemplate day,
                                         List<PrescribedExercise> prescriptions,
                                         Map<Long, List<PrescribedSet>> setsByExercise,
                                         Map<Long, Exercise> exercisesById) {

        // ---------- 取出周修饰 ----------
        // null 一律按「无调整」处理，而不是报错——
        // 不限期计划或用户没配周期化时，week 就是空的
        BigDecimal weightAdjustPct = (week == null || week.getWeightAdjustPct() == null)
                ? BigDecimal.ZERO
                : week.getWeightAdjustPct();
        int setAdjust = (week == null || week.getSetAdjust() == null)
                ? 0
                : week.getSetAdjust();

        List<ExpandedWorkout.ExerciseItem> items = new ArrayList<>(prescriptions.size());

        for (PrescribedExercise pe : prescriptions) {
            Exercise exercise = exercisesById.get(pe.getExerciseId());

            // ---------- 组数：先应用周调整，再夹到合理范围 ----------
            int baseSets = pe.getTargetSets() == null ? 1 : pe.getTargetSets();
            int targetSets = clamp(baseSets + setAdjust, MIN_SETS, MAX_SETS);

            List<PrescribedSet> prescribedSets = setsByExercise.getOrDefault(pe.getId(), List.of());

            List<ExpandedWorkout.SetItem> setItems = new ArrayList<>(targetSets);
            for (int setNumber = 1; setNumber <= targetSets; setNumber++) {
                // 逐组处方可能不存在——「3组×10次」这种统一目标就不建逐组记录。
                // 找不到时传 null，由 resolve 系列方法回落到动作级默认值。
                PrescribedSet ps = findSet(prescribedSets, setNumber);
                setItems.add(expandSet(setNumber, pe, ps, weightAdjustPct, exercise));
            }

            items.add(new ExpandedWorkout.ExerciseItem(
                    pe.getExerciseId(),
                    exercise == null ? null : exercise.getName(),
                    exercise == null || exercise.getPrimaryMuscle() == null
                            ? null : exercise.getPrimaryMuscle().name(),
                    exercise == null || exercise.getPrimaryMuscle() == null
                            ? null : exercise.getPrimaryMuscle().getDisplayName(),
                    exercise == null || exercise.getMetricType() == null
                            ? null : exercise.getMetricType().name(),
                    pe.getOrderIndex(),
                    // 超级组信息原样带出去。
                    // ⚠️ 展开算法**不改变**超级组语义——它只影响执行顺序，
                    // 不影响任何数值。所以这里只是透传，没有任何 if (isSuperset) 分支。
                    pe.getSupersetGroup(),
                    pe.getOrderInGroup(),
                    setItems,
                    pe.getNote()
            ));
        }

        return new ExpandedWorkout(
                program.getId(),
                program.getName(),
                week == null ? null : week.getWeekNumber(),
                day.getDayNumber(),
                day.getName(),
                week != null && week.isDeloadWeek(),
                weightAdjustPct,
                items
        );
    }

    // ==================================================================
    // 单组展开
    // ==================================================================

    private static ExpandedWorkout.SetItem expandSet(int setNumber,
                                                     PrescribedExercise pe,
                                                     PrescribedSet ps,
                                                     BigDecimal weightAdjustPct,
                                                     Exercise exercise) {
        ExpandedWorkout.Target target = resolveTarget(pe, ps, weightAdjustPct, exercise);

        // ---------- 次数 ----------
        Reps reps = resolveReps(pe, ps);

        // ---------- 休息 ----------
        Integer restSec = firstNonNull(
                ps == null ? null : ps.getRestSec(),
                pe.getRestSec(),
                DEFAULT_REST_SEC);

        // ---------- 组类型 ----------
        SetType setType = (ps == null || ps.getSetType() == null)
                ? SetType.WORKING
                : ps.getSetType();

        return new ExpandedWorkout.SetItem(
                setNumber,
                setType,
                setType.getDisplayName(),
                target,
                reps.fixed(),
                reps.min(),
                reps.max(),
                restSec,
                ps == null ? null : ps.getNote()
        );
    }

    /**
     * 解析目标强度 —— **优先级链**。
     *
     * <h3>为什么写成线性的「依次尝试」而不是嵌套 if</h3>
     *
     * <p>朴素写法长这样：
     * <pre>
     *   if (ps != null) {
     *       if (ps.getTargetWeight() != null) { ... }
     *       else if (ps.getTargetWeightPct() != null) { ... }
     *       else if (ps.getTargetRpe() != null) { ... }
     *       else {
     *           if (pe.getTargetWeight() != null) { ... }
     *           else if (...) { ... }
     *       }
     *   } else {
     *       if (pe.getTargetWeight() != null) { ... }
     *       ...
     *   }
     * </pre>
     * 两个层级各三种类型，嵌套深度已经到 4 层，而且**回落到动作级的那份逻辑
     * 要写两遍**（ps 为 null 时一遍，ps 有值但三个字段都为空时一遍）。
     * 将来加第四种重量类型，要改四个地方。
     *
     * <p>这里改成：**每一层各自解析成一个 Target（解析不出来就是 null），
     * 然后用 {@code firstNonNull} 串起来**。
     * 优先级链变成一行，加一层只加一个参数。
     */
    private static ExpandedWorkout.Target resolveTarget(PrescribedExercise pe,
                                                        PrescribedSet ps,
                                                        BigDecimal weightAdjustPct,
                                                        Exercise exercise) {
        ExpandedWorkout.Target target = firstNonNull(
                targetFromSet(ps),          // 第 1 优先级：逐组处方
                targetFromExercise(pe)      // 第 2 优先级：动作级默认
        );

        if (target == null) {
            // 既没有重量也没有 RPE —— 比如纯自重动作只写了次数。
            // 这是合法的，不是错误。
            return null;
        }

        // ---------- V1 只支持绝对重量 ----------
        //
        // ⚠️ 这里**明确报错，而不是静默忽略**。
        //
        // 静默忽略的话，用户看到的会是一个缺少重量的处方，
        // 他可能以为「这个动作不用重量」——这是错的，而且很危险。
        // 报错至少让他知道该改什么。
        if (target.type() != TargetWeightType.ABSOLUTE) {
            throw new BizException(ErrorCode.PROGRAM_TARGET_UNSUPPORTED, String.format(
                    "动作「%s」使用了 %s 作为目标重量，当前版本暂不支持。"
                            + "请改为填写具体的公斤数",
                    exercise == null ? "未知动作" : exercise.getName(),
                    target.type().getDisplayName()));
        }

        return ExpandedWorkout.Target.ofWeight(applyWeekAdjust(target.weight(), weightAdjustPct));
    }

    /**
     * 应用周修饰。
     *
     * <p><b>语义：{@code weightAdjustPct} 是「调整量」而不是「目标值」。</b>
     * <pre>
     *   weightAdjustPct = +5   → 基准 × 1.05   （加 5%）
     *   weightAdjustPct = -40  → 基准 × 0.60   （减 40%，即 deload 到六成）
     *   weightAdjustPct = 0    → 基准不变
     * </pre>
     *
     * <p><b>这个语义必须显式写死并在文档里说清</b>——两种理解
     * （「减 40%」vs「降到 40%」）算出来的重量差 20%，
     * 而且不会有任何报错，只会让用户莫名其妙地练轻或练重。
     *
     * <p>选「调整量」的理由：字段名叫 {@code adjust}（调整**量**），
     * 而且 deload 的通用说法就是「减量 40%」。
     */
    private static BigDecimal applyWeekAdjust(BigDecimal base, BigDecimal adjustPct) {
        if (base == null) {
            return null;
        }
        if (adjustPct == null || adjustPct.signum() == 0) {
            return base;
        }

        // 除以 100 时保留 4 位小数，避免 2.5/100 这种除不尽的情况丢精度
        BigDecimal multiplier = BigDecimal.ONE.add(
                adjustPct.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));

        BigDecimal adjusted = base.multiply(multiplier)
                .setScale(2, RoundingMode.HALF_UP);

        // 夹到最小有效重量，防止极端 deload 算出 0 或负数
        return adjusted.max(MIN_WEIGHT);
    }

    /** 从逐组处方解析目标强度。三个字段按 重量 → 百分比 → RPE 检查 */
    private static ExpandedWorkout.Target targetFromSet(PrescribedSet ps) {
        if (ps == null) {
            return null;
        }
        // 逐组处方表没有 target_weight_type 字段——
        // 因为三条记录里最多只有一条会填值，靠「哪个非空」就能唯一确定类型，
        // 再加一个类型列是冗余的（而且可能出现类型与值不一致的脏数据）。
        if (ps.getTargetWeight() != null) {
            return ExpandedWorkout.Target.ofWeight(ps.getTargetWeight());
        }
        if (ps.getTargetWeightPct() != null) {
            return ExpandedWorkout.Target.ofPct(ps.getTargetWeightPct());
        }
        if (ps.getTargetRpe() != null) {
            return ExpandedWorkout.Target.ofRpe(ps.getTargetRpe());
        }
        return null;
    }

    /**
     * 从动作级默认值解析目标强度。
     *
     * <p>这里用 {@code targetWeightType} 显式判别，而不是像逐组那样
     * 「哪个非空用哪个」——因为动作级的类型是一次**声明**
     * （「这个动作按 %1RM 练」），即使对应字段暂时为空，
     * 类型本身也是有意义的。
     */
    private static ExpandedWorkout.Target targetFromExercise(PrescribedExercise pe) {
        if (pe == null || pe.getTargetWeightType() == null) {
            return null;
        }
        return switch (pe.getTargetWeightType()) {
            case ABSOLUTE -> pe.getTargetWeight() == null
                    ? null : ExpandedWorkout.Target.ofWeight(pe.getTargetWeight());
            case PERCENT_1RM -> pe.getTargetWeightPct() == null
                    ? null : ExpandedWorkout.Target.ofPct(pe.getTargetWeightPct());
            case RPE -> pe.getTargetRpe() == null
                    ? null : ExpandedWorkout.Target.ofRpe(pe.getTargetRpe());
        };
    }

    /** 解析目标次数。逐组优先，回落动作级 */
    private static Reps resolveReps(PrescribedExercise pe, PrescribedSet ps) {
        // 逐组的固定次数
        if (ps != null && ps.getTargetReps() != null) {
            return new Reps(ps.getTargetReps(), null, null);
        }
        // 逐组的次数区间
        if (ps != null && (ps.getTargetRepsMin() != null || ps.getTargetRepsMax() != null)) {
            return new Reps(null, ps.getTargetRepsMin(), ps.getTargetRepsMax());
        }
        // 动作级
        Integer min = pe.getTargetRepsMin();
        Integer max = pe.getTargetRepsMax();
        if (min != null && min.equals(max)) {
            // 上下限相同时是固定次数，归一化成 fixed 形式，
            // 避免前端要处理「min == max 但要当固定值用」这种特殊情况
            return new Reps(min, null, null);
        }
        return new Reps(null, min, max);
    }

    // ==================================================================
    // 小工具
    // ==================================================================

    /** 目标次数的三种形态：固定 / 区间 / 无 */
    private record Reps(Integer fixed, Integer min, Integer max) {
    }

    /** 从逐组处方列表里找第 N 组 */
    private static PrescribedSet findSet(List<PrescribedSet> sets, int setNumber) {
        for (PrescribedSet s : sets) {
            if (s.getSetNumber() != null && s.getSetNumber() == setNumber) {
                return s;
            }
        }
        return null;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 返回第一个非 null 的参数。
     *
     * <p>这就是优先级链的实现——用可变参数而不是重载，
     * 将来加一层只加一个参数，不用新增方法。
     *
     * <p>⚠️ 泛型方法配合可变参数会产生「unchecked generic array creation」警告，
     * 加 {@code @SafeVarargs} 抑制。这里确实安全——
     * 我们只读数组元素，不做类型转换也不存储它。
     */
    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) {
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
