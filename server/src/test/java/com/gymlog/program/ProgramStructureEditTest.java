package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import com.gymlog.program.dto.ProgramCreateRequest;
import com.gymlog.program.dto.ProgramDetailResponse;
import com.gymlog.program.dto.ProgramStructureRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 计划结构编辑（全量替换 + 乐观锁）。
 *
 * <p><b>为什么这个测试必须是集成测试</b>：
 * 它验证的核心行为是「删掉旧的、写进新的、版本号 +1」，
 * 这三件事的**原子性**是重点——中间任何一步失败都不能留下半截数据。
 * 原子性只有真的连上数据库才测得出来。
 */
@SpringBootTest
@Transactional
class ProgramStructureEditTest {

    private static final Long USER = 1L;

    @Autowired private ProgramService programService;
    @Autowired private ProgramMapper programMapper;
    @Autowired private DayTemplateMapper dayTemplateMapper;
    @Autowired private PrescribedExerciseMapper prescribedExerciseMapper;
    @Autowired private ExerciseMapper exerciseMapper;

    private Long benchId;
    private Long squatId;

    @BeforeEach
    void setUp() {
        benchId = findExercise("杠铃卧推").getId();
        // ⚠️ 是「杠铃深蹲」不是「深蹲」——「深蹲」只是别名。
        // findExercise 按 name 精确匹配，写错会直接断言失败而不是静默取到 null
        squatId = findExercise("杠铃深蹲").getId();
    }

    // ==================================================================
    // 一、替换本身
    // ==================================================================

    @Test
    @DisplayName("编辑结构后，旧动作被换掉，新动作生效")
    void replacesStructure() {
        Long programId = programService.create(USER, createRequest(benchId, "推日"));

        // 把「推日」里的卧推换成深蹲
        ProgramDetailResponse after = programService.updateStructure(USER, programId,
                new ProgramStructureRequest(
                        List.of(week(1, "0", 0)),
                        List.of(day(1, "腿日", prescription(squatId, 1, 5, 5, 5, 180))),
                        1));

        assertThat(after.days()).hasSize(1);
        assertThat(after.days().get(0).name()).isEqualTo("腿日");

        List<PrescribedExercise> stored = prescribedExerciseMapper.selectList(
                new LambdaQueryWrapper<PrescribedExercise>()
                        .eq(PrescribedExercise::getDayTemplateId, after.days().get(0).id()));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getExerciseId()).isEqualTo(squatId);
        assertThat(stored.get(0).getTargetSets()).isEqualTo(5);
    }

    @Test
    @DisplayName("提交的训练日变少了，多出来的会被删掉")
    void removesOmittedDays() {
        ProgramCreateRequest.DayRequest d1 = day(1, "推日", prescription(benchId, 1, 3, 8, 10, 120));
        ProgramCreateRequest.DayRequest d2 = day(2, "腿日", prescription(squatId, 1, 5, 5, 5, 180));

        Long programId = programService.create(USER, new ProgramCreateRequest(
                "两日计划", null, 4, null, List.of(week(1, "0", 0)), List.of(d1, d2)));

        assertThat(dayTemplateMapper.selectCount(
                new LambdaQueryWrapper<DayTemplate>().eq(DayTemplate::getProgramId, programId)))
                .isEqualTo(2);

        // 只提交第 1 天 —— 第 2 天应该消失
        ProgramDetailResponse after = programService.updateStructure(USER, programId,
                new ProgramStructureRequest(List.of(week(1, "0", 0)), List.of(d1), 1));

        assertThat(after.days()).hasSize(1);
        assertThat(after.days().get(0).dayNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("子记录 id 每次编辑都会变 —— 这是设计的已知代价")
    void childIdsChurnOnEveryEdit() {
        Long programId = programService.create(USER, createRequest(benchId, "推日"));

        ProgramDetailResponse first = programService.detail(USER, programId);
        Long originalDayId = first.days().get(0).id();

        // 原样提交一次，什么都不改
        ProgramDetailResponse second = programService.updateStructure(USER, programId,
                new ProgramStructureRequest(List.of(week(1, "0", 0)),
                        List.of(day(1, "推日", prescription(benchId, 1, 3, 8, 10, 120))), 1));

        // ⚠️ 这个断言看起来在「确认一个 bug」，其实是在**固定一个设计契约**。
        //
        // 全量替换必然导致 id 变化。这条断言的价值在于：
        // 如果哪天有人把实现改成「按 id 增量更新」，这个测试会红，
        // 提醒他同时确认「会话快照不引用这些 id」这个前提还成立。
        //
        // 前提一旦被破坏（会话开始外键引用 prescribed_exercise），
        // 历史会话的快照就会指向不存在的行。
        assertThat(second.days().get(0).id())
                .as("全量替换会改变子记录 id，会话快照必须存值而不是外键")
                .isNotEqualTo(originalDayId);
    }

    // ==================================================================
    // 二、乐观锁
    // ==================================================================

    @Nested
    @DisplayName("乐观锁")
    class OptimisticLocking {

        @Test
        @DisplayName("每次成功的编辑让版本号 +1")
        void bumpsVersion() {
            Long programId = programService.create(USER, createRequest(benchId, "推日"));
            assertThat(programMapper.selectById(programId).getVersion()).isEqualTo(1);

            ProgramDetailResponse v2 = programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(List.of(week(1, "5", 0)), List.of(), 1));
            assertThat(v2.version()).isEqualTo(2);

            ProgramDetailResponse v3 = programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(List.of(week(1, "10", 0)), List.of(), 2));
            assertThat(v3.version()).isEqualTo(3);
        }

        @Test
        @DisplayName("版本号对不上时拒绝写入，并且不碰旧数据")
        void rejectsStaleVersion() {
            Long programId = programService.create(USER, createRequest(benchId, "推日"));

            // 第一次编辑成功，版本变成 2，周调整改成 +5%
            programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(List.of(week(1, "5", 0)),
                            List.of(day(1, "推日", prescription(benchId, 1, 3, 8, 10, 120))), 1));

            // 另一台设备还拿着 version=1 来提交，想把周调整改成 +99% 并把训练日删光
            assertThatThrownBy(() -> programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(List.of(week(1, "99", 0)), List.of(), 1)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROGRAM_VERSION_CONFLICT);

            // ⚠️ 关键断言：冲突时旧数据必须**原封不动**。
            // 如果实现是先删后校验版本，这里就没有训练日了。
            ProgramDetailResponse current = programService.detail(USER, programId);
            assertThat(current.version()).isEqualTo(2);
            assertThat(current.weeks()).hasSize(1);
            assertThat(current.weeks().get(0).weightAdjustPct()).isEqualByComparingTo("5");
            assertThat(current.days()).hasSize(1);
        }

        @Test
        @DisplayName("改名称不动版本号 —— 否则会让正在编辑结构的设备莫名冲突")
        void renamingDoesNotBumpVersion() {
            Long programId = programService.create(USER, createRequest(benchId, "推日"));

            programService.updateMeta(USER, programId, "改了个名字", "新说明");

            // 版本号仍然可以用 1 提交 —— 改名没有让其他设备失效
            ProgramDetailResponse after = programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(List.of(week(1, "5", 0)), List.of(), 1));
            assertThat(after.version()).isEqualTo(2);
            assertThat(after.name()).isEqualTo("改了个名字");
        }
    }

    // ==================================================================
    // 三、校验与守卫
    // ==================================================================

    @Nested
    @DisplayName("校验与守卫")
    class Guards {

        @Test
        @DisplayName("已归档的计划不能编辑")
        void rejectsArchivedProgram() {
            Long programId = programService.create(USER, createRequest(benchId, "推日"));

            Program archive = new Program();
            archive.setId(programId);
            archive.setStatus(ProgramStatus.ARCHIVED);
            programMapper.updateById(archive);

            assertThatThrownBy(() -> programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(List.of(), List.of(), 1)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROGRAM_NOT_EDITABLE);
        }

        @Test
        @DisplayName("结构校验失败时旧数据完好 —— 校验必须在删除之前")
        void validationFailureLeavesDataIntact() {
            Long programId = programService.create(USER, createRequest(benchId, "推日"));

            // 造一个非法结构：超级组只给一个动作（至少要两个）
            ProgramCreateRequest.PrescriptionRequest lonely = new ProgramCreateRequest.PrescriptionRequest(
                    benchId, 1, 1, 1, 3, 8, 10, 120,
                    TargetWeightType.ABSOLUTE, new BigDecimal("60"), null, null, null, null);

            assertThatThrownBy(() -> programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(
                            List.of(week(1, "0", 0)),
                            List.of(day(1, "推日", lonely)), 1)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SUPERSET_GROUP_INVALID);

            // ⚠️ 这条断言防的是「把校验放到删除之后」这种改动。
            // 那种写法在正常路径下完全看不出问题，
            // 但只要用户提交一份非法结构，他的整个计划就被清空了。
            ProgramDetailResponse current = programService.detail(USER, programId);
            assertThat(current.days()).hasSize(1);
            assertThat(current.days().get(0).exercises()).hasSize(1);
        }

        @Test
        @DisplayName("别人的计划不能编辑，且不泄露它是否存在")
        void rejectsOtherUsersProgram() {
            Long programId = programService.create(USER, createRequest(benchId, "推日"));

            assertThatThrownBy(() -> programService.updateStructure(999L, programId,
                    new ProgramStructureRequest(List.of(), List.of(), 1)))
                    .isInstanceOf(BizException.class)
                    .extracting(e -> ((BizException) e).getErrorCode())
                    .isEqualTo(ErrorCode.PROGRAM_NOT_FOUND);
        }

        @Test
        @DisplayName("不能引用别人私有的自定义动作")
        void rejectsForeignCustomExercise() {
            // 用户 999 建了一个私有动作
            Exercise priv = new Exercise();
            priv.setUserId(999L);
            priv.setName("[测试]别人的私有动作");
            priv.setPrimaryMuscle(com.gymlog.exercise.MuscleGroup.CHEST);
            priv.setEquipment(com.gymlog.exercise.Equipment.BARBELL);
            priv.setMetricType(com.gymlog.exercise.MetricType.WEIGHT_REPS);
            priv.setIsUnilateral(0);
            priv.setStatus(Exercise.STATUS_ENABLED);
            exerciseMapper.insert(priv);

            Long programId = programService.create(USER, createRequest(benchId, "推日"));

            // 校验在删除之前，所以旧数据也不该被动
            assertThatThrownBy(() -> programService.updateStructure(USER, programId,
                    new ProgramStructureRequest(List.of(week(1, "0", 0)),
                            List.of(day(1, "推日", prescription(priv.getId(), 1, 3, 8, 10, 120))), 1)))
                    .isInstanceOf(BizException.class);

            assertThat(programService.detail(USER, programId).days()).hasSize(1);
        }
    }

    // ==================================================================
    // 测试数据构造
    // ==================================================================

    private static ProgramCreateRequest createRequest(Long exerciseId, String dayName) {
        return new ProgramCreateRequest(
                "测试计划", null, 4, null,
                List.of(week(1, "0", 0)),
                List.of(day(1, dayName, prescription(exerciseId, 1, 3, 8, 10, 120))));
    }

    private static ProgramCreateRequest.WeekRequest week(int number, String adjustPct, int setAdjust) {
        return new ProgramCreateRequest.WeekRequest(
                number, 3, new BigDecimal(adjustPct), setAdjust, false, null);
    }

    private static ProgramCreateRequest.DayRequest day(int number, String name,
                                                       ProgramCreateRequest.PrescriptionRequest... exercises) {
        return new ProgramCreateRequest.DayRequest(number, name, false, null, List.of(exercises));
    }

    private static ProgramCreateRequest.PrescriptionRequest prescription(
            Long exerciseId, int order, int sets, int repsMin, int repsMax, int restSec) {
        return new ProgramCreateRequest.PrescriptionRequest(
                exerciseId, order, null, null, sets, repsMin, repsMax, restSec,
                TargetWeightType.ABSOLUTE, new BigDecimal("60"), null, null, null, null);
    }

    private Exercise findExercise(String name) {
        Exercise e = exerciseMapper.selectOne(
                new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID)
                        .eq(Exercise::getName, name));
        assertThat(e).as("动作库里应存在：" + name).isNotNull();
        return e;
    }
}
