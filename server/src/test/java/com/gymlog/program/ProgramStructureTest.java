package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.training.SetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证计划五张表的完整链路能否正确读写。
 *
 * <p><b>为什么光验证「表建出来了」不够</b>：
 * 这是一个五层嵌套的结构，单看每张表的字段都对，不代表它们能组合出
 * 一个真实的计划。这个测试搭一套「8 周推拉腿」的骨架，
 * 把三层关系的读写都走一遍。
 */
@SpringBootTest
@Transactional
class ProgramStructureTest {

    @Autowired private ProgramMapper programMapper;
    @Autowired private WeekTemplateMapper weekTemplateMapper;
    @Autowired private DayTemplateMapper dayTemplateMapper;
    @Autowired private PrescribedExerciseMapper prescribedExerciseMapper;
    @Autowired private PrescribedSetMapper prescribedSetMapper;
    @Autowired private ExerciseMapper exerciseMapper;

    @Test
    @DisplayName("能搭出完整的计划结构并原样读回")
    void shouldBuildAndReadFullProgramStructure() {
        // 从动作库里挑两个动作来用
        Exercise bench = findExercise("杠铃卧推");
        Exercise row = findExercise("杠铃划船");

        // ---------- 1. 计划 ----------
        Program program = new Program();
        program.setUserId(1L);
        program.setName("8周增肌计划");
        program.setTotalWeeks(8);
        program.setStartDate(LocalDate.of(2026, 9, 21));
        program.setVersion(1);
        program.setStatus(ProgramStatus.ACTIVE);
        programMapper.insert(program);

        // rootId 指向自己（第一个版本）
        program.setRootId(program.getId());
        programMapper.updateById(program);

        assertThat(program.getId()).isNotNull();
        assertThat(program.isOpenEnded()).isFalse();

        // ---------- 2. 周模板：第 1 周基准，第 8 周 deload ----------
        WeekTemplate week1 = newWeek(program.getId(), 1, BigDecimal.ZERO, 0);
        WeekTemplate week8 = newWeek(program.getId(), 8, new BigDecimal("-40"), -1);
        week8.setIsDeload(1);
        weekTemplateMapper.insert(week1);
        weekTemplateMapper.insert(week8);

        assertThat(week1.isDeloadWeek()).isFalse();
        assertThat(week8.isDeloadWeek()).isTrue();

        // ---------- 3. 训练日 ----------
        DayTemplate pushDay = new DayTemplate();
        pushDay.setProgramId(program.getId());
        pushDay.setDayNumber(1);
        pushDay.setName("推日 A");
        pushDay.setIsRestDay(0);
        dayTemplateMapper.insert(pushDay);

        // ---------- 4. 处方动作：一个普通动作 + 一个超级组 ----------
        PrescribedExercise benchPrescription = newPrescription(
                pushDay.getId(), bench.getId(), 1, 4, 8, 10, 120);
        prescribedExerciseMapper.insert(benchPrescription);

        // 超级组：动作 A1 和 A2 交替做
        PrescribedExercise ssA1 = newPrescription(
                pushDay.getId(), bench.getId(), 2, 3, 10, 10, 0);
        ssA1.setSupersetGroup(1);
        ssA1.setOrderInGroup(1);
        prescribedExerciseMapper.insert(ssA1);

        PrescribedExercise ssA2 = newPrescription(
                pushDay.getId(), row.getId(), 3, 3, 10, 10, 90);
        ssA2.setSupersetGroup(1);
        ssA2.setOrderInGroup(2);
        prescribedExerciseMapper.insert(ssA2);

        assertThat(benchPrescription.isInSuperset()).isFalse();
        assertThat(ssA1.isInSuperset()).isTrue();
        assertThat(benchPrescription.isRepRange()).isTrue();   // 8-10 是区间

        // ---------- 5. 逐组处方：5×5 递增 ----------
        PrescribedExercise squatPrescription = newPrescription(
                pushDay.getId(), bench.getId(), 4, 5, 5, 5, 180);
        prescribedExerciseMapper.insert(squatPrescription);

        BigDecimal[] weights = {
                new BigDecimal("60"), new BigDecimal("65"), new BigDecimal("70"),
                new BigDecimal("70"), new BigDecimal("70")
        };
        for (int i = 0; i < weights.length; i++) {
            PrescribedSet set = new PrescribedSet();
            set.setPrescribedExerciseId(squatPrescription.getId());
            set.setSetNumber(i + 1);
            // 第 1 组是热身
            set.setSetType(i == 0 ? SetType.WARMUP : SetType.WORKING);
            set.setTargetReps(5);
            set.setTargetWeight(weights[i]);
            prescribedSetMapper.insert(set);
        }

        // ---------- 6. 按层级读回，验证关系正确 ----------
        List<WeekTemplate> weeks = weekTemplateMapper.selectList(
                new LambdaQueryWrapper<WeekTemplate>()
                        .eq(WeekTemplate::getProgramId, program.getId())
                        .orderByAsc(WeekTemplate::getWeekNumber));
        assertThat(weeks).hasSize(2);
        assertThat(weeks.get(0).getWeekNumber()).isEqualTo(1);
        assertThat(weeks.get(1).getWeightAdjustPct()).isEqualByComparingTo("-40");

        List<PrescribedExercise> prescriptions = prescribedExerciseMapper.selectList(
                new LambdaQueryWrapper<PrescribedExercise>()
                        .eq(PrescribedExercise::getDayTemplateId, pushDay.getId())
                        .orderByAsc(PrescribedExercise::getOrderIndex));
        assertThat(prescriptions).hasSize(4);

        // 超级组的两个动作
        List<PrescribedExercise> superset = prescriptions.stream()
                .filter(PrescribedExercise::isInSuperset)
                .sorted((a, b) -> a.getOrderInGroup().compareTo(b.getOrderInGroup()))
                .toList();
        assertThat(superset).hasSize(2);
        assertThat(superset.get(0).getOrderInGroup()).isEqualTo(1);
        assertThat(superset.get(1).getOrderInGroup()).isEqualTo(2);

        // 逐组处方
        List<PrescribedSet> sets = prescribedSetMapper.selectList(
                new LambdaQueryWrapper<PrescribedSet>()
                        .eq(PrescribedSet::getPrescribedExerciseId, squatPrescription.getId())
                        .orderByAsc(PrescribedSet::getSetNumber));
        assertThat(sets).hasSize(5);
        assertThat(sets.get(0).getSetType()).isEqualTo(SetType.WARMUP);
        assertThat(sets.get(2).getTargetWeight()).isEqualByComparingTo("70");

        // 容量统计口径：第 1 组是热身，不计入
        long workingSets = sets.stream().filter(s -> s.getSetType().countsTowardVolume()).count();
        assertThat(workingSets).isEqualTo(4);
    }

    // ==================================================================

    private Exercise findExercise(String name) {
        Exercise e = exerciseMapper.selectOne(
                new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID)
                        .eq(Exercise::getName, name));
        assertThat(e).as("动作库里应存在：" + name).isNotNull();
        return e;
    }

    private WeekTemplate newWeek(Long programId, int weekNumber,
                                 BigDecimal adjustPct, int setAdjust) {
        WeekTemplate w = new WeekTemplate();
        w.setProgramId(programId);
        w.setWeekNumber(weekNumber);
        w.setSessionsPerWeek(3);
        w.setWeightAdjustPct(adjustPct);
        w.setSetAdjust(setAdjust);
        w.setIsDeload(0);
        return w;
    }

    private PrescribedExercise newPrescription(Long dayId, Long exerciseId, int order,
                                               int sets, int repsMin, int repsMax, int restSec) {
        PrescribedExercise p = new PrescribedExercise();
        p.setDayTemplateId(dayId);
        p.setExerciseId(exerciseId);
        p.setOrderIndex(order);
        p.setTargetSets(sets);
        p.setTargetRepsMin(repsMin);
        p.setTargetRepsMax(repsMax);
        p.setRestSec(restSec);
        p.setTargetWeightType(TargetWeightType.ABSOLUTE);
        return p;
    }
}
