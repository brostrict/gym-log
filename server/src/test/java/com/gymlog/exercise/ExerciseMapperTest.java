package com.gymlog.exercise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link Exercise} 与 {@code exercise} 表的映射。
 *
 * <p><b>重点验证枚举映射</b>：Java 枚举 ↔ 数据库 VARCHAR 的往返
 * 是这一步最容易出问题的地方——配错了往往不是编译错误，
 * 而是运行时读到 null 或抛 {@code IllegalArgumentException}。
 *
 * <h3>⚠️ 为什么测试数据的名字都带 {@value #TEST_PREFIX} 前缀</h3>
 *
 * <p>{@code exercise} 表上有唯一索引 {@code uk_exercise_user_name(user_id, name)}，
 * 而 V5 已经把 90 个内置动作写进了库——里面就有「杠铃卧推」和「引体向上」。
 *
 * <p>这个测试最早写的时候还没有种子数据，直接用真实动作名做测试数据；
 * 种子加进来之后，{@code user_id = 0} 的那条就撞了唯一索引，
 * **两个测试从此一直是红的**。
 *
 * <p>更糟的是它红得很隐蔽：报错是 {@code DuplicateKeyException}，
 * 看起来像「插入逻辑坏了」，而实际上插入逻辑完全正常，
 * 是测试数据选得不合适。
 *
 * <p>教训：**测试数据不要用生产数据里可能出现的名字**。
 * 加个前缀，测试才是自洽的——不必知道种子数据里有什么。
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class ExerciseMapperTest {

    /** 测试数据前缀。保证与种子数据、以及其他测试的数据都不冲突 */
    private static final String TEST_PREFIX = "[测试]";

    @Autowired
    private ExerciseMapper exerciseMapper;

    @Test
    @DisplayName("插入内置动作后能查回，枚举字段正确往返")
    void shouldRoundTripEnumFields() {
        // ---------- 1. 插入一个内置动作 ----------
        Exercise exercise = new Exercise();
        exercise.setUserId(Exercise.BUILT_IN_USER_ID);
        exercise.setName(TEST_PREFIX + "杠铃卧推");
        exercise.setAlias("卧推,bench press");
        exercise.setPrimaryMuscle(MuscleGroup.CHEST);
        exercise.setSecondaryMuscles("SHOULDERS,ARMS");
        exercise.setEquipment(Equipment.BARBELL);
        exercise.setMovementPattern(MovementPattern.HORIZONTAL_PUSH);
        exercise.setMetricType(MetricType.WEIGHT_REPS);
        exercise.setIsUnilateral(0);
        exercise.setStatus(Exercise.STATUS_ENABLED);
        exercise.setSortOrder(10);

        assertThat(exerciseMapper.insert(exercise)).isEqualTo(1);
        assertThat(exercise.getId()).isNotNull().isPositive();

        // ---------- 2. 查回并验证枚举往返 ----------
        Exercise found = exerciseMapper.selectById(exercise.getId());
        assertThat(found).isNotNull();

        // 这四个是本步最关键的断言：
        // 如果 MyBatis 的枚举映射配错了，这里会得到 null 或抛异常
        assertThat(found.getPrimaryMuscle()).isEqualTo(MuscleGroup.CHEST);
        assertThat(found.getEquipment()).isEqualTo(Equipment.BARBELL);
        assertThat(found.getMovementPattern()).isEqualTo(MovementPattern.HORIZONTAL_PUSH);
        assertThat(found.getMetricType()).isEqualTo(MetricType.WEIGHT_REPS);

        // 中文名称（数据库是 utf8mb4，中文往返必须无损）
        assertThat(found.getName()).isEqualTo(TEST_PREFIX + "杠铃卧推");
        assertThat(found.getAlias()).isEqualTo("卧推,bench press");
    }

    @Test
    @DisplayName("自重动作的 bw_factor 正确往返")
    void shouldRoundTripBwFactor() {
        Exercise pullUp = new Exercise();
        pullUp.setUserId(Exercise.BUILT_IN_USER_ID);
        pullUp.setName(TEST_PREFIX + "引体向上");
        pullUp.setPrimaryMuscle(MuscleGroup.BACK);
        pullUp.setEquipment(Equipment.BODYWEIGHT);
        pullUp.setMovementPattern(MovementPattern.VERTICAL_PULL);
        pullUp.setMetricType(MetricType.REPS_ONLY);
        pullUp.setBwFactor(new BigDecimal("1.00"));
        pullUp.setIsUnilateral(0);
        pullUp.setStatus(Exercise.STATUS_ENABLED);

        exerciseMapper.insert(pullUp);
        Exercise found = exerciseMapper.selectById(pullUp.getId());

        // BigDecimal 用 isEqualByComparingTo 而不是 isEqualTo：
        // 前者比较数值（1.0 == 1.00），后者比较精度。
        // 数据库 DECIMAL(4,2) 读回来是 1.00，而 new BigDecimal("1.0") 是 1.0，
        // 用 isEqualTo 会失败——这是 BigDecimal 的经典陷阱。
        assertThat(found.getBwFactor()).isEqualByComparingTo("1.00");

        // 负重动作不需要 bw_factor，应该是 null
        Exercise benchPress = new Exercise();
        benchPress.setUserId(Exercise.BUILT_IN_USER_ID);
        benchPress.setName(TEST_PREFIX + "负重卧推");
        benchPress.setPrimaryMuscle(MuscleGroup.CHEST);
        benchPress.setEquipment(Equipment.BARBELL);
        benchPress.setMetricType(MetricType.WEIGHT_REPS);
        benchPress.setIsUnilateral(0);
        benchPress.setStatus(Exercise.STATUS_ENABLED);
        exerciseMapper.insert(benchPress);

        assertThat(exerciseMapper.selectById(benchPress.getId()).getBwFactor()).isNull();
    }

    @Test
    @DisplayName("内置与自定义动作的判定逻辑正确")
    void shouldDistinguishBuiltInFromCustom() {
        Exercise builtIn = new Exercise();
        builtIn.setUserId(Exercise.BUILT_IN_USER_ID);
        builtIn.setName(TEST_PREFIX + "深蹲");
        builtIn.setPrimaryMuscle(MuscleGroup.LEGS);
        builtIn.setEquipment(Equipment.BARBELL);
        builtIn.setMetricType(MetricType.WEIGHT_REPS);
        builtIn.setIsUnilateral(0);
        builtIn.setStatus(Exercise.STATUS_ENABLED);
        exerciseMapper.insert(builtIn);

        Exercise custom = new Exercise();
        custom.setUserId(999L);
        custom.setName(TEST_PREFIX + "我的自定义动作");
        custom.setPrimaryMuscle(MuscleGroup.LEGS);
        custom.setEquipment(Equipment.BODYWEIGHT);
        custom.setMetricType(MetricType.REPS_ONLY);
        custom.setIsUnilateral(0);
        custom.setStatus(Exercise.STATUS_ENABLED);
        exerciseMapper.insert(custom);

        assertThat(exerciseMapper.selectById(builtIn.getId()).isBuiltIn()).isTrue();
        assertThat(exerciseMapper.selectById(custom.getId()).isBuiltIn()).isFalse();

        // 归属判断：用户 999 拥有 custom，但拥有不了 builtIn
        assertThat(exerciseMapper.selectById(custom.getId()).isOwnedBy(999L)).isTrue();
        assertThat(exerciseMapper.selectById(custom.getId()).isOwnedBy(1L)).isFalse();
        assertThat(exerciseMapper.selectById(builtIn.getId()).isOwnedBy(999L)).isFalse();
    }
}
