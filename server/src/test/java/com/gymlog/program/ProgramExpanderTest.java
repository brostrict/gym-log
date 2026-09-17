package com.gymlog.program;

import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Equipment;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.MetricType;
import com.gymlog.exercise.MuscleGroup;
import com.gymlog.program.dto.ExpandedWorkout;
import com.gymlog.training.SetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 周期化展开算法的单元测试。
 *
 * <p><b>⚠️ 注意这个测试类没有 {@code @SpringBootTest}。</b>
 *
 * <p>这是把展开逻辑做成纯函数换来的直接好处：
 * <pre>
 *   带容器：启动 Spring + 连数据库 ≈ 5-10 秒/类
 *   不带容器：≈ 50 毫秒/类
 * </pre>
 *
 * <p>速度不是唯一原因。更关键的是**测起来简单**——
 * 上面的边界情况里有一半（周修饰算出负数、组数被减到 0、
 * 逐组处方缺失）在数据库里很难造出对应的数据，
 * 但在这里就是构造一个对象的事。
 *
 * <p>所以：**逻辑越纯，边界情况测起来越便宜，测试也就写得越多。**
 */
class ProgramExpanderTest {

    // ==================================================================
    // 一、基准展开
    // ==================================================================

    @Test
    @DisplayName("没有周修饰时，按动作级的基准值展开")
    void expandsBaselineWhenNoWeek() {
        DayTemplate day = day(1, "推日");
        PrescribedExercise bench = prescription(1001L, 1L, 1, 3);
        bench.setTargetRepsMin(10);
        bench.setTargetRepsMax(10);
        bench.setRestSec(120);
        bench.setTargetWeightType(TargetWeightType.ABSOLUTE);
        bench.setTargetWeight(new BigDecimal("60"));

        ExpandedWorkout result = expand(day, null, List.of(bench));

        assertThat(result.weekNumber()).isNull();
        assertThat(result.deloadWeek()).isFalse();
        assertThat(result.weightAdjustPct()).isEqualByComparingTo("0");

        ExpandedWorkout.ExerciseItem item = only(result);
        assertThat(item.exerciseName()).isEqualTo("杠铃卧推");
        // 3 组，不多不少
        assertThat(item.sets()).hasSize(3);

        ExpandedWorkout.SetItem first = item.sets().get(0);
        assertThat(first.setNumber()).isEqualTo(1);
        assertThat(first.target().weight()).isEqualByComparingTo("60");
        // 10-10 归一化成固定次数，而不是留一个 min == max 的区间给前端处理
        assertThat(first.targetReps()).isEqualTo(10);
        assertThat(first.targetRepsMin()).isNull();
        assertThat(first.targetRepsMax()).isNull();
        assertThat(first.restSec()).isEqualTo(120);
        assertThat(first.setType()).isEqualTo(SetType.WORKING);
    }

    // ==================================================================
    // 二、周修饰
    // ==================================================================

    @Nested
    @DisplayName("周修饰")
    class WeekAdjustment {

        @Test
        @DisplayName("加重周：+5% 让 60kg 变成 63kg")
        void appliesPositiveAdjustment() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = weightedBench(3, "60");

            ExpandedWorkout result = expand(day, week(5, "5", 0, false), List.of(bench));

            assertThat(result.weekNumber()).isEqualTo(5);
            assertThat(result.weightAdjustPct()).isEqualByComparingTo("5");
            assertThat(only(result).sets())
                    .allSatisfy(s -> assertThat(s.target().weight()).isEqualByComparingTo("63"));
        }

        @Test
        @DisplayName("减量周：-40% 是「减掉 40%」而不是「降到 40%」")
        void appliesDeloadAsReduction() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = weightedBench(3, "60");

            ExpandedWorkout result = expand(day, week(4, "-40", 0, true), List.of(bench));

            assertThat(result.deloadWeek()).isTrue();
            // 60 × (1 - 0.40) = 36
            //
            // 若按「降到 40%」理解，结果是 24。
            // 两者差 12kg——**这个断言就是防止语义被改坏**。
            assertThat(only(result).sets())
                    .allSatisfy(s -> assertThat(s.target().weight()).isEqualByComparingTo("36"));
        }

        @Test
        @DisplayName("小数百分比不丢精度：2.5% 让 80kg 变成 82kg")
        void handlesFractionalPercentage() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = weightedBench(1, "80");

            ExpandedWorkout result = expand(day, week(2, "2.5", 0, false), List.of(bench));

            // 80 × 1.025 = 82.00
            assertThat(only(result).sets().get(0).target().weight()).isEqualByComparingTo("82");
        }

        @Test
        @DisplayName("组数调整为正：3 组变 4 组")
        void appliesPositiveSetAdjustment() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = weightedBench(3, "60");

            ExpandedWorkout result = expand(day, week(6, "0", 1, false), List.of(bench));

            assertThat(only(result).sets()).hasSize(4);
        }

        @Test
        @DisplayName("组数被减到 0 时夹到 1 组，而不是展开成空训练")
        void clampsSetsToMinimumOne() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = weightedBench(2, "60");

            // 减量周减 3 组：2 - 3 = -1
            ExpandedWorkout result = expand(day, week(4, "-40", -3, true), List.of(bench));

            // 归零的话，用户打开跟练界面会看到「这个动作 0 组」，
            // 而正确行为是「减量周练 1 组」
            assertThat(only(result).sets()).hasSize(1);
            assertThat(only(result).sets().get(0).setNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("极端减重不会算出负数重量")
        void clampsWeightToMinimum() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = weightedBench(1, "1");

            // 1kg 减 90% = 0.1kg，低于最小配重
            ExpandedWorkout result = expand(day, week(4, "-90", 0, true), List.of(bench));

            // 宁可显示 0.5kg，也不能显示 0.1kg 或负数——
            // 用户看到「-3.2kg」会以为程序坏了
            assertThat(only(result).sets().get(0).target().weight())
                    .isEqualByComparingTo("0.5");
        }
    }

    // ==================================================================
    // 三、优先级链
    // ==================================================================

    @Nested
    @DisplayName("优先级链：逐组处方 > 动作级默认")
    class PriorityChain {

        @Test
        @DisplayName("逐组处方覆盖动作级的重量与次数")
        void setLevelOverridesExerciseLevel() {
            DayTemplate day = day(1, "推日");

            // 动作级：3 组 × 10 次 @ 60kg
            PrescribedExercise bench = weightedBench(3, "60");
            bench.setTargetRepsMin(10);
            bench.setTargetRepsMax(10);

            // 第 3 组单独指定：5 次 @ 70kg（递增组）
            Map<Long, List<PrescribedSet>> sets = new HashMap<>();
            sets.put(1001L, List.of(
                    prescribedSet(3, SetType.WORKING)
                            .withReps(5)
                            .withWeight("70")
                            .build()));

            ExpandedWorkout result = expand(day, null, List.of(bench), sets);
            List<ExpandedWorkout.SetItem> items = only(result).sets();

            // 前两组用动作级默认值
            assertThat(items.get(0).target().weight()).isEqualByComparingTo("60");
            assertThat(items.get(0).targetReps()).isEqualTo(10);
            assertThat(items.get(1).target().weight()).isEqualByComparingTo("60");

            // 第三组用逐组处方
            assertThat(items.get(2).target().weight()).isEqualByComparingTo("70");
            assertThat(items.get(2).targetReps()).isEqualTo(5);
        }

        @Test
        @DisplayName("逐组只覆盖了重量时，次数仍回落到动作级")
        void setLevelFallsBackPerField() {
            DayTemplate day = day(1, "推日");

            PrescribedExercise bench = weightedBench(1, "60");
            bench.setTargetRepsMin(8);
            bench.setTargetRepsMax(12);

            // 只指定重量，不指定次数——次数应该仍然用动作级的 8-12
            Map<Long, List<PrescribedSet>> sets = new HashMap<>();
            sets.put(1001L, List.of(
                    prescribedSet(1, SetType.WORKING).withWeight("65").build()));

            ExpandedWorkout.SetItem item = only(expand(day, null, List.of(bench), sets)).sets().get(0);

            assertThat(item.target().weight()).isEqualByComparingTo("65");
            assertThat(item.targetRepsMin()).isEqualTo(8);
            assertThat(item.targetRepsMax()).isEqualTo(12);
        }

        @Test
        @DisplayName("休息时间的三级回落：逐组 > 动作级 > 默认 90 秒")
        void restFallsBackThroughThreeLevels() {
            DayTemplate day = day(1, "推日");

            PrescribedExercise bench = weightedBench(3, "60");
            bench.setRestSec(120);

            Map<Long, List<PrescribedSet>> sets = new HashMap<>();
            sets.put(1001L, List.of(
                    prescribedSet(1, SetType.WORKING).withRest(45).build()));

            List<ExpandedWorkout.SetItem> items =
                    only(expand(day, null, List.of(bench), sets)).sets();

            assertThat(items.get(0).restSec()).isEqualTo(45);   // 逐组
            assertThat(items.get(1).restSec()).isEqualTo(120);  // 动作级
            assertThat(items.get(2).restSec()).isEqualTo(120);
        }

        @Test
        @DisplayName("动作级也没写休息时用 90 秒默认值，而不是 null")
        void restDefaultsToNinety() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = weightedBench(1, "60");
            bench.setRestSec(null);

            // restSec 为 null 会让跟练界面的倒计时无从下手。
            // 展开阶段必须给出一个确定的数字。
            assertThat(only(expand(day, null, List.of(bench))).sets().get(0).restSec())
                    .isEqualTo(90);
        }
    }

    // ==================================================================
    // 四、不支持的目标类型
    // ==================================================================

    @Nested
    @DisplayName("V1 只支持绝对重量")
    class UnsupportedTargets {

        @Test
        @DisplayName("RPE 目标明确报错，并且错误信息里带动作名")
        void rejectsRpeTarget() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = new PrescriptionBuilder(1001L, 1L, 3)
                    .withRpe("8").build();

            assertThatThrownBy(() -> expand(day, null, List.of(bench)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("杠铃卧推")
                    .hasMessageContaining("RPE")
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROGRAM_TARGET_UNSUPPORTED);
        }

        @Test
        @DisplayName("%1RM 目标明确报错，而不是静默算出一个错的重量")
        void rejectsPercentOneRepMax() {
            DayTemplate day = day(1, "推日");
            PrescribedExercise bench = new PrescriptionBuilder(1001L, 1L, 3)
                    .withPct("75").build();

            assertThatThrownBy(() -> expand(day, null, List.of(bench)))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("杠铃卧推");

            // 这里**故意选择报错而不是静默跳过**：
            // 静默跳过的话，用户会看到一份没有重量的处方，
            // 可能以为「这个动作不用负重」——照着练是危险的。
        }

        @Test
        @DisplayName("完全没有目标强度是合法的（纯自重动作只写次数）")
        void allowsNullTarget() {
            DayTemplate day = day(1, "推日");

            PrescribedExercise pushUp = prescription(1001L, 1L, 1, 3);
            pushUp.setTargetRepsMin(15);
            pushUp.setTargetRepsMax(20);
            // 不设 targetWeightType —— 自重动作

            List<ExpandedWorkout.SetItem> items =
                    only(expand(day, null, List.of(pushUp))).sets();

            assertThat(items).hasSize(3);
            assertThat(items.get(0).target()).isNull();
            assertThat(items.get(0).targetRepsMin()).isEqualTo(15);
        }
    }

    // ==================================================================
    // 五、超级组与顺序
    // ==================================================================

    @Test
    @DisplayName("超级组信息原样透传，数值不受任何影响")
    void passesSupersetThroughWithoutChangingValues() {
        DayTemplate day = day(1, "推日");

        PrescribedExercise bench = weightedBench(2, "60");
        bench.setOrderIndex(1);
        bench.setSupersetGroup(1);
        bench.setOrderInGroup(1);

        PrescribedExercise fly = weightedBench(2, "20");
        fly.setId(1002L);
        fly.setExerciseId(2L);
        fly.setOrderIndex(2);
        fly.setSupersetGroup(1);
        fly.setOrderInGroup(2);

        // 超级组内的动作：划船 60kg 而 fly 20kg——
        // 重量各不相同，因为超级组只改变**执行节奏**，不改变数值
        ExpandedWorkout result = expand(day, week(3, "5", 0, false),
                List.of(bench, fly), Map.of(), exercises());

        assertThat(result.exercises()).hasSize(2);
        assertThat(result.exercises().get(0).supersetGroup()).isEqualTo(1);
        assertThat(result.exercises().get(0).orderInGroup()).isEqualTo(1);
        assertThat(result.exercises().get(1).supersetGroup()).isEqualTo(1);
        assertThat(result.exercises().get(1).orderInGroup()).isEqualTo(2);

        // 周修饰对两个动作一视同仁
        assertThat(result.exercises().get(0).sets().get(0).target().weight())
                .isEqualByComparingTo("63");
        assertThat(result.exercises().get(1).sets().get(0).target().weight())
                .isEqualByComparingTo("21");
    }

    // ==================================================================
    // 六、元信息
    // ==================================================================

    @Test
    @DisplayName("休息日标记能正确带出计划与训练日的元信息")
    void carriesMetaInformation() {
        DayTemplate day = day(3, "腿日");
        day.setId(200L);

        ExpandedWorkout result = expand(day, week(7, "0", 0, false),
                List.of(weightedBench(1, "100")), Map.of(), exercises());

        assertThat(result.programId()).isEqualTo(12L);
        assertThat(result.programName()).isEqualTo("测试计划");
        assertThat(result.dayNumber()).isEqualTo(3);
        assertThat(result.dayName()).isEqualTo("腿日");
        assertThat(result.weekNumber()).isEqualTo(7);
    }

    @Test
    @DisplayName("动作信息缺失时不抛异常，字段留空")
    void toleratesMissingExercise() {
        DayTemplate day = day(1, "推日");

        // 处方指向一个 exercisesById 里没有的动作 id。
        // 正常流程不会发生（创建计划时校验过可见性），
        // 但管理员删动作、或数据迁移出问题时可能遇到。
        // 此时**宁可显示一行没有名字的动作，也不能让整个页面打不开**。
        PrescribedExercise orphan = prescription(1001L, 999L, 1, 2);

        ExpandedWorkout result = expand(day, null, List.of(orphan), Map.of(), exercises());

        assertThat(only(result).exerciseName()).isNull();
        assertThat(only(result).primaryMuscle()).isNull();
        assertThat(only(result).sets()).hasSize(2);
    }

    // ==================================================================
    // 测试脚手架
    // ==================================================================

    private static final long BENCH_ID = 1L;
    private static final long ROW_ID = 2L;

    /** 展开并断言结果里恰好有一个动作，返回它 */
    private static ExpandedWorkout.ExerciseItem only(ExpandedWorkout workout) {
        assertThat(workout.exercises()).hasSize(1);
        return workout.exercises().get(0);
    }

    private static ExpandedWorkout expand(DayTemplate day,
                                          WeekTemplate week,
                                          List<PrescribedExercise> prescriptions) {
        return expand(day, week, prescriptions, Map.of(), exercises());
    }

    private static ExpandedWorkout expand(DayTemplate day,
                                          WeekTemplate week,
                                          List<PrescribedExercise> prescriptions,
                                          Map<Long, List<PrescribedSet>> sets) {
        return expand(day, week, prescriptions, sets, exercises());
    }

    private static ExpandedWorkout expand(DayTemplate day,
                                          WeekTemplate week,
                                          List<PrescribedExercise> prescriptions,
                                          Map<Long, List<PrescribedSet>> sets,
                                          Map<Long, Exercise> exercises) {
        Program program = new Program();
        program.setId(12L);
        program.setName("测试计划");

        return ProgramExpander.expand(program, week, day, prescriptions, sets, exercises);
    }

    private static Map<Long, Exercise> exercises() {
        Exercise bench = new Exercise();
        bench.setId(BENCH_ID);
        bench.setName("杠铃卧推");
        bench.setPrimaryMuscle(MuscleGroup.CHEST);
        bench.setEquipment(Equipment.BARBELL);
        bench.setMetricType(MetricType.WEIGHT_REPS);

        Exercise row = new Exercise();
        row.setId(ROW_ID);
        row.setName("杠铃划船");
        row.setPrimaryMuscle(MuscleGroup.BACK);
        row.setEquipment(Equipment.BARBELL);
        row.setMetricType(MetricType.WEIGHT_REPS);

        return Map.of(BENCH_ID, bench, ROW_ID, row);
    }

    private static DayTemplate day(int number, String name) {
        DayTemplate d = new DayTemplate();
        d.setId(100L + number);
        d.setProgramId(12L);
        d.setDayNumber(number);
        d.setName(name);
        d.setIsRestDay(0);
        return d;
    }

    private static WeekTemplate week(Integer number, String weightAdjustPct,
                                     Integer setAdjust, boolean deload) {
        WeekTemplate w = new WeekTemplate();
        w.setProgramId(12L);
        w.setWeekNumber(number);
        w.setWeightAdjustPct(weightAdjustPct == null
                ? null : new BigDecimal(weightAdjustPct));
        w.setSetAdjust(setAdjust);
        w.setIsDeload(deload ? 1 : 0);
        return w;
    }

    /** 建一个 ABSOLUTE 处方的动作，组数与重量直接给定 */
    private static PrescribedExercise weightedBench(int sets, String weight) {
        return new PrescriptionBuilder(1001L, BENCH_ID, sets).withWeight(weight).build();
    }

    private static SetBuilder prescribedSet(int setNumber, SetType type) {
        return new SetBuilder(setNumber, type);
    }

    private static PrescribedExercise prescription(long id, long exerciseId,
                                                   int orderIndex, int sets) {
        PrescribedExercise pe = new PrescribedExercise();
        pe.setId(id);
        pe.setDayTemplateId(101L);
        pe.setExerciseId(exerciseId);
        pe.setOrderIndex(orderIndex);
        pe.setTargetSets(sets);
        return pe;
    }

    /** 处方动作的构造器 —— 让每个测试只写它关心的字段 */
    private static final class PrescriptionBuilder {
        private final PrescribedExercise pe = new PrescribedExercise();

        PrescriptionBuilder(long id, long exerciseId, int sets) {
            pe.setId(id);
            pe.setDayTemplateId(101L);
            pe.setExerciseId(exerciseId);
            pe.setOrderIndex(1);
            pe.setTargetSets(sets);
            pe.setRestSec(90);
        }

        PrescriptionBuilder withWeight(String weight) {
            pe.setTargetWeightType(TargetWeightType.ABSOLUTE);
            pe.setTargetWeight(new BigDecimal(weight));
            return this;
        }

        PrescriptionBuilder withPct(String pct) {
            pe.setTargetWeightType(TargetWeightType.PERCENT_1RM);
            pe.setTargetWeightPct(new BigDecimal(pct));
            return this;
        }

        PrescriptionBuilder withRpe(String rpe) {
            pe.setTargetWeightType(TargetWeightType.RPE);
            pe.setTargetRpe(new BigDecimal(rpe));
            return this;
        }

        PrescribedExercise build() {
            return pe;
        }
    }

    /**
     * 逐组处方的构造器。
     *
     * <p>和 {@link PrescriptionBuilder} 分开是必须的——
     * 两者是**不同的实体**（{@code prescribed_exercise} vs
     * {@code prescribed_set}），字段也不同。
     * 共用一个构建器会让测试里出现「能设 targetSets 的逐组处方」
     * 这种现实中不存在的对象。
     */
    private static final class SetBuilder {
        private final PrescribedSet ps = new PrescribedSet();

        SetBuilder(int setNumber, SetType type) {
            ps.setId(9000L + setNumber);
            ps.setPrescribedExerciseId(1001L);
            ps.setSetNumber(setNumber);
            ps.setSetType(type);
        }

        SetBuilder withReps(int reps) {
            ps.setTargetReps(reps);
            return this;
        }

        SetBuilder withWeight(String weight) {
            ps.setTargetWeight(new BigDecimal(weight));
            return this;
        }

        SetBuilder withRest(int restSec) {
            ps.setRestSec(restSec);
            return this;
        }

        PrescribedSet build() {
            return ps;
        }
    }
}
