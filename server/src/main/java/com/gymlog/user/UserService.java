package com.gymlog.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gymlog.common.BizException;
import com.gymlog.common.ErrorCode;
import com.gymlog.common.JwtService;
import com.gymlog.user.dto.LoginRequest;
import com.gymlog.user.dto.LoginResponse;
import com.gymlog.user.dto.RegisterRequest;
import com.gymlog.user.dto.TokenResponse;
import com.gymlog.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /**
     * 一个固定的 BCrypt 哈希，用于「用户不存在时也跑一次密码校验」。
     *
     * <p>见 {@link #login} 里对时序攻击的说明。
     * 这个哈希对应的明文是什么并不重要——校验结果会被直接丢弃。
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

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

        // ---------- 绑定默认角色 ----------
        //
        // 放在注册流程里（而不是等到用的时候再补），有两个原因：
        //   ① 每个用户从诞生起就应该有明确的角色，不存在「无角色用户」这种中间态
        //   ② 整个 register 方法在同一个事务里——角色绑定失败会连同用户一起回滚，
        //      不会留下一个没有角色的半成品用户
        assignRole(user.getId(), Role.CODE_USER);

        log.info("用户注册成功 | id={} | email={}", user.getId(), email);
        return user.getId();
    }

    /**
     * 给用户授予角色。
     *
     * <p><b>为什么要先查 role 表拿到 id</b>：{@code user_role} 存的是
     * {@code role_id} 外键，不是角色编码。这是关系型数据库的常规做法——
     * 存 id 比存字符串省空间，且改编码时不用更新所有关联行。
     *
     * @throws BizException 角色不存在时抛出（正常情况下不会发生——
     *                      种子数据在 V3 迁移里已插入；真发生了说明部署有问题，
     *                      应该快速失败而不是静默跳过）
     */
    private void assignRole(Long userId, String roleCode) {
        Role role = roleMapper.selectOne(
                new LambdaQueryWrapper<Role>().eq(Role::getCode, roleCode)
        );
        if (role == null) {
            // 这是配置/部署错误，不是业务错误。抛 BizException 会返回 500，
            // 日志里能看到完整上下文，便于快速定位。
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "角色不存在：" + roleCode + "，请检查 V3 迁移是否执行成功");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(role.getId());
        userRoleMapper.insert(userRole);
    }

    /**
     * 登录，验证凭据并签发 access token + refresh token。
     *
     * @param ip        客户端 IP，记录在 refresh token 上供审计
     * @param userAgent 客户端 UA，同上
     * @throws BizException 邮箱或密码不正确（{@link ErrorCode#PASSWORD_INCORRECT}）、
     *                      账号被禁用（{@link ErrorCode#ACCOUNT_DISABLED}）
     */
    @Transactional
    public LoginResponse login(LoginRequest request, String ip, String userAgent) {
        String email = normalizeEmail(request.getEmail());
        User user = findByEmail(email);

        // ---------- 第一道关：时序攻击防护 ----------
        //
        // 天真写法是：if (user == null) throw PASSWORD_INCORRECT;
        // 看起来没问题，但会泄露「这个邮箱是否注册过」：
        //
        //   邮箱不存在 → 直接抛异常，耗时 ~1ms
        //   邮箱存在   → 跑一次 BCrypt 比对，耗时 ~80ms
        //
        // 攻击者不需要看报错内容，**只看响应时间**就能批量判断哪些邮箱注册过。
        // 这叫「用户名枚举」，是攻击的第一步——拿到有效邮箱后再针对性撞库。
        //
        // 修法：用户不存在时，也拿一个假哈希跑一次 BCrypt。
        // 两种情况的耗时变得接近，时序信号就消失了。
        if (user == null) {
            passwordEncoder.matches(request.getPassword(), DUMMY_HASH);
            throw new BizException(ErrorCode.PASSWORD_INCORRECT);
        }

        // ---------- 第二道关：密码比对 ----------
        //
        // matches() 做的事：从哈希串里拆出盐和 cost，用同样的参数
        // 把明文密码再算一遍，比对结果。所以不需要单独存盐。
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            // ⚠️ 注意返回的错误码和「用户不存在」时**完全一样**。
            // 如果分成「用户不存在」和「密码错误」两种提示，
            // 等于直接告诉攻击者哪些邮箱是有效的。
            throw new BizException(ErrorCode.PASSWORD_INCORRECT);
        }

        // ---------- 第三道关：账号状态 ----------
        //
        // 放在密码校验**之后**：如果先检查状态，攻击者就能通过
        // 「返回『账号已禁用』还是『密码错误』」来枚举账号。
        // 只有密码正确的人，才配知道这个账号被禁用了。
        if (user.getStatus() != null && user.getStatus() == User.STATUS_DISABLED) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // ---------- 更新最后登录时间 ----------
        // 只 set 需要改的字段：MyBatis-Plus 的 updateById 会跳过 null 字段，
        // 所以这里构造一个「只有 id 和 lastLoginAt」的对象就够了，
        // 不会把其他字段覆盖成 null。
        User touch = new User();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(touch);

        // ---------- 签发令牌对 ----------
        //
        // access token：JWT，无状态，1 小时有效
        // refresh token：随机字符串，**存库**，30 天有效
        //
        // 为什么 refresh token 要存库而不是也做成 JWT：
        // 因为要支持「主动撤销」——用户登出、改密码、手机丢失，
        // 都需要让它立刻失效。纯 JWT 是无状态的，签发后无法提前作废。
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = refreshTokenService.issue(user.getId(), ip, userAgent);

        log.info("用户登录成功 | id={} | email={}", user.getId(), email);

        return LoginResponse.of(
                accessToken,
                refreshToken,
                jwtService.getAccessTokenTtlSeconds(),
                new LoginResponse.UserBrief(user.getId(), user.getEmail(), user.getNickname())
        );
    }

    /**
     * 用 refresh token 换取新的令牌对（**令牌轮换**）。
     *
     * <p><b>什么是令牌轮换（Token Rotation）</b>：每次刷新时，
     * 旧的 refresh token 立即作废，同时签发一个**全新的**。
     *
     * <p>为什么不复用同一个 refresh token：那样的话，一个 refresh token
     * 在 30 天里可以被反复使用，一旦泄露就是 30 天的持续访问权。
     * 轮换之后，泄露的 token 最多只能用一次（甚至可能因为已被使用而失效）。
     *
     * <p><b>轮换还带来了「重放检测」的能力</b>：
     * <pre>
     *   正常流程：客户端持 RT-1 → 刷新 → 拿到 RT-2，RT-1 作废
     *   攻击场景：攻击者偷到 RT-1 并使用 → 拿到 RT-3
     *             真正的用户下次用 RT-1 刷新 → 发现它已作废
     *             → 说明 RT-1 被泄露过！→ 撤销该用户全部令牌
     * </pre>
     * V1 暂未实现「检测到已撤销 token 被使用时撤销全部」，
     * 只做基础的轮换。这个增强在 Phase 6 安全加固时补。
     *
     * @throws BizException refresh token 无效、已撤销或已过期时抛出
     */
    @Transactional
    public TokenResponse refresh(String rawRefreshToken, String ip, String userAgent) {
        RefreshToken stored = refreshTokenService.findUsable(rawRefreshToken);
        if (stored == null) {
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 用户在持有有效 refresh token 期间可能被管理员禁用了，
        // 所以这里要重新查一次用户状态——不能想当然地认为「token 有效 = 用户可用」
        User user = userMapper.selectById(stored.getUserId());
        if (user == null) {
            // 用户被删了但 token 还没过期。撤销掉这个孤儿 token，避免每次都查一遍
            refreshTokenService.revoke(stored);
            throw new BizException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (user.getStatus() != null && user.getStatus() == User.STATUS_DISABLED) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        // ---------- 轮换：旧的立即作废，签发全新的 ----------
        refreshTokenService.revoke(stored);

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = refreshTokenService.issue(user.getId(), ip, userAgent);

        log.info("刷新令牌成功 | userId={}", user.getId());

        return TokenResponse.of(newAccessToken, newRefreshToken,
                jwtService.getAccessTokenTtlSeconds());
    }

    /**
     * 登出：撤销当前设备的 refresh token。
     *
     * <p><b>注意这里撤销的是 refresh token，不是 access token。</b>
     * access token 是无状态 JWT，服务端**无法**让它提前失效——
     * 它会继续有效直到自然过期（最多 1 小时）。
     *
     * <p>这是双 token 设计的一个已知取舍：**登出后 access token 仍有
     * 最长 1 小时的残留有效期**。要彻底解决只能引入黑名单（存已撤销的
     * access token 直到过期），但那等于放弃了无状态的优势——
     * 每个请求都要查一次黑名单，还不如直接用有状态 session。
     *
     * <p>1 小时的窗口是业界普遍接受的做法。如果要更短，把
     * {@code jwt.access-token-ttl} 调小即可，代价是刷新更频繁。
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken stored = refreshTokenService.findUsable(rawRefreshToken);

        // 即使 token 已经无效也不报错——登出应该是幂等的。
        // 用户点两次登出、或者 token 刚好过期，都不该看到错误提示。
        if (stored != null) {
            refreshTokenService.revoke(stored);
            log.info("用户登出 | userId={}", stored.getUserId());
        }
    }

    /**
     * 查询用户资料。
     *
     * <p><b>为什么要有「用户不存在」这个分支</b>：正常情况下不会走到——
     * userId 是从合法 token 里解出来的，用户必然存在。
     *
     * <p>但有两种例外：
     * <ol>
     *   <li><b>用户在 token 有效期内被删除了</b>。token 还没过期，
     *       但数据库里的记录已经没了。这时应该返回 404 而不是抛空指针。</li>
     *   <li><b>token 是伪造的</b>（虽然验签应该挡住，但多一层防御没坏处）。</li>
     * </ol>
     * 这也是**越权防护的体现**：查询条件带 userId，用户只能看到自己的数据。
     */
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return UserProfileResponse.from(user);
    }

    /**
     * 按邮箱查用户。
     *
     * <p>用 {@code selectOne} + {@code LambdaQueryWrapper}，
     * 字段名通过方法引用指定，编译期可检查。
     */
    private User findByEmail(String email) {
        return userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
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
