package com.gymlog.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link User} 实体与 {@code user} 表之间的映射是否正确。
 *
 * <p><b>为什么加 {@code @Transactional}</b>：
 * Spring 测试里的 {@code @Transactional} 会在每个测试方法结束后
 * <b>自动回滚事务</b>。所以测试插入的数据不会留在数据库里，
 * 不需要手写清理代码，也不会污染开发数据。
 *
 * <p>注意这和「测试事务」是两件事——这里的事务是 Spring 测试框架
 * 主动开启并回滚的，不是业务代码里的事务。
 *
 * <p><b>{@code @SpringBootTest} 做了什么</b>：启动完整的 Spring 容器
 * （读配置、连数据库、跑 Flyway、注册 Bean），和真实运行环境一致。
 * 代价是启动慢（几秒），所以只用于集成测试，
 * 纯逻辑的单元测试不该用它。
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class UserMapperTest {

    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("插入用户后能按 id 查回，且字段映射正确")
    void shouldInsertAndSelectUser() {
        // ---------- 1. 插入 ----------
        User user = new User();
        user.setEmail("mapper-test@example.com");
        user.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        user.setNickname("测试用户");
        user.setGender(User.GENDER_MALE);
        user.setBirthYear(1995);
        user.setHeightCm(new BigDecimal("175.5"));
        user.setGoal("MUSCLE_GAIN");
        user.setExperience("INTERMEDIATE");

        int affected = userMapper.insert(user);
        assertThat(affected).isEqualTo(1);

        // 主键回填：IdType.AUTO 会把数据库生成的自增 id 写回对象
        assertThat(user.getId()).isNotNull().isPositive();

        // ---------- 2. 按 id 查回 ----------
        User found = userMapper.selectById(user.getId());
        assertThat(found).isNotNull();

        // 验证 camelCase ↔ snake_case 映射：
        // passwordHash → password_hash、birthYear → birth_year、
        // heightCm → height_cm、unitPref → unit_pref
        assertThat(found.getEmail()).isEqualTo("mapper-test@example.com");
        assertThat(found.getPasswordHash()).isEqualTo("$2a$10$abcdefghijklmnopqrstuv");
        assertThat(found.getNickname()).isEqualTo("测试用户");
        assertThat(found.getBirthYear()).isEqualTo(1995);
        assertThat(found.getGoal()).isEqualTo("MUSCLE_GAIN");

        // BigDecimal 比较要注意：assertThat(a).isEqualTo(b) 比较的是
        // compareTo 语义（1.0 与 1.00 相等），这正是我们想要的
        assertThat(found.getHeightCm()).isEqualByComparingTo("175.5");

        // ---------- 3. 验证数据库默认值生效 ----------
        // 插入时这些字段没赋值，MyBatis-Plus 会跳过 null 字段，
        // 由建表语句里的 DEFAULT 填上
        assertThat(found.getStatus()).isEqualTo(User.STATUS_NORMAL);
        assertThat(found.getUnitPref()).isEqualTo("kg");
        assertThat(found.getDeleted()).isZero();
        assertThat(found.getEmailVerified()).isZero();
        assertThat(found.getProvider()).isEqualTo("local");

        // DEFAULT CURRENT_TIMESTAMP 由数据库填
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
    }
}
