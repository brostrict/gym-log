package com.gymlog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密配置。
 *
 * <p><b>为什么不直接 new 一个用，而要注册成 Bean</b>：
 * <ol>
 *   <li><b>统一算法</b>：注册时加密、登录时校验，用的必须是同一个编码器。
 *       如果两处各自 {@code new}，将来改强度参数（cost）时很容易漏改一处，
 *       导致「新用户能注册但登录不了」这种极难排查的问题。</li>
 *   <li><b>可替换</b>：将来要换成 Argon2 或加一层 pepper，只改这一个地方。</li>
 *   <li><b>可测试</b>：测试里能注入一个固定盐的实现。</li>
 * </ol>
 *
 * <p>注意注入时用接口类型 {@link PasswordEncoder}，不是具体类——
 * 这样换实现时调用方一行都不用改。
 */
@Configuration
public class PasswordConfig {

    /**
     * 密码编码器。
     *
     * <p><b>BCrypt 为什么比 MD5/SHA 适合存密码</b>：
     * <table border="1">
     *   <tr><th></th><th>MD5 / SHA-256</th><th>BCrypt</th></tr>
     *   <tr><td>速度</td><td>极快（GPU 每秒数十亿次）</td><td><b>故意设计得慢</b></td></tr>
     *   <tr><td>盐</td><td>需手动管理</td><td><b>内置</b>，存在哈希串里</td></tr>
     *   <tr><td>抗暴力破解</td><td>弱</td><td>强</td></tr>
     * </table>
     *
     * <p>关键在「故意慢」：MD5 快到可以用彩虹表批量撞，而 BCrypt 单次计算
     * 就要几十毫秒——攻击者试一百万个密码要花掉几十小时。
     * <b>对正常登录来说几十毫秒无感，对暴力破解来说就是天堑。</b>
     *
     * <p><b>cost 参数（强度因子）</b>：默认 10，表示迭代 2^10 = 1024 次。
     * 每 +1 计算耗时翻倍。生产环境建议调到 12（约 4 倍耗时，仍在百毫秒内）。
     * 调高不影响已有哈希——**cost 存在哈希串自身里**，
     * 所以老密码用老 cost 验证，新密码用新 cost 生成，可以平滑升级。
     *
     * <p>哈希串形如：
     * <pre>
     *   $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
     *    │  │  └── 22 字符的盐 + 31 字符的哈希（Base64）
     *    │  └───── cost = 10
     *    └──────── 算法版本
     * </pre>
     * 所以验证时不需要单独存盐——盐就在哈希串里。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
