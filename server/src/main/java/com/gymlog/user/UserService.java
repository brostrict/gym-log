package com.gymlog.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.user.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 用户业务逻辑。
 *
 * <p><b>为什么不写 {@code UserService} 接口 + {@code UserServiceImpl} 实现</b>：
 * 只有一个实现类时，接口是纯粹的负担——改一个方法签名要动两个文件，
 * IDE 跳转多一跳，而且没有任何实际收益。
 *
 * <p>接口的价值在于「有多种实现」或「需要跨模块解耦」。
 * 本项目两者都不需要。Spring 官方团队近年也明确表态：
 * 不要为了「看起来规范」而写没有必要的接口。
 *
 * <p><b>构造器注入而非字段注入</b>：{@code final} 字段 + Lombok 的
 * {@code @RequiredArgsConstructor} 生成构造器，Spring 自动用它注入。
 * 相比 {@code @Autowired} 直接标在字段上：
 * <ul>
 *   <li>依赖不可变（final），不会被后续代码改掉</li>
 *   <li>不依赖 Spring 容器也能 new 出来测试</li>
 *   <li>循环依赖会在启动时**直接报错**，而不是运行时才炸——
 *       字段注入允许循环依赖存在，问题被推迟到运行期</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * 注册新用户。
     *
     * @return 新用户的 ID
     * @throws BizException 邮箱已被占用时抛出 {@link ErrorCode#EMAIL_ALREADY_EXISTS}
     */
    @Transactional
    public Long register(RegisterRequest request) {
        // 邮箱统一转小写再处理。
        // MySQL 的排序规则 utf8mb4_0900_ai_ci 是大小写不敏感的，
        // 所以 'A@x.com' 和 'a@x.com' 在库里本来就会被判为重复。
        // 但**存进去的值**不会自动统一，所以这里主动归一化，
        // 避免同一个人的邮箱在库里出现两种大小写写法。
        String email = normalizeEmail(request.getEmail());

        // ---------- 第一层防护：应用层查重 ----------
        // 目的不是「保证唯一」（第二层才是），而是给出友好的错误提示。
        // 绝大多数重复注册是用户自己忘了注册过，这一层能覆盖 99% 的情况。
        if (existsByEmail(email)) {
            throw new BizException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        // 明文密码在这里被加密。注意：加密后原始密码就丢失了，
        // 任何人（包括我们自己）都无法还原——这是有意的设计。
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname().trim());

        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // ---------- 第二层防护：数据库唯一索引 ----------
            // 上面「查重」和这里「插入」之间存在时间窗：
            //
            //   T1: 请求A 查邮箱 → 不存在
            //   T2: 请求B 查邮箱 → 不存在        ← 两个请求都通过了检查
            //   T3: 请求A 插入 → 成功
            //   T4: 请求B 插入 → 唯一索引冲突 ✗
            //
            // 这不是 bug，是并发场景的固有性质。应用层的「先查后插」
            // 永远无法保证唯一性，**真正的保证只能来自数据库的唯一索引**。
            //
            // 捕获后转成同样的业务错误码——对用户来说，
            // 「你注册太快了」和「这个邮箱注册过了」是同一种结果。
            log.warn("注册时唯一索引冲突（并发注册同一邮箱）| email={}", email);
            throw new BizException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        log.info("用户注册成功 | id={} | email={}", user.getId(), email);
        return user.getId();
    }

    /**
     * 判断邮箱是否已存在。
     *
     * <p>用 {@code LambdaQueryWrapper} 而不是手写 SQL——字段名通过方法引用
     * {@code User::getEmail} 指定，**编译期就能检查**。
     * 如果哪天把字段改名了，这里会直接编译失败，而不是等到运行时才发现
     * 「Unknown column 'email'」。
     */
    private boolean existsByEmail(String email) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        return count != null && count > 0;
    }

    /**
     * 邮箱归一化：去首尾空格 + 转小写。
     *
     * <p>用 {@code Locale.ROOT} 而不是默认 Locale——
     * 土耳其语的 Locale 下，'I'.toLowerCase() 会得到 'ı' 而不是 'i'，
     * 会导致同一个邮箱在不同地区设置下被当成两个不同的值。
     * 这是著名的「土耳其 I 问题」。
     */
    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
