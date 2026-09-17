package com.gymlog.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT 配置项，对应 application.yml 中的 {@code jwt.*}。
 *
 * <p><b>{@code @ConfigurationProperties} 比 {@code @Value} 好在哪</b>：
 * <ul>
 *   <li><b>成组绑定</b>：一类配置写在一个类里，而不是散落在各处的
 *       {@code @Value("${jwt.secret}")}。想找「JWT 有哪些配置」一眼可见。</li>
 *   <li><b>类型安全</b>：{@code Duration} 能自动解析 {@code 1h}、{@code 30d}
 *       这种写法；{@code @Value} 拿到的是字符串，还得自己转。</li>
 *   <li><b>可校验</b>：可以配合 {@code @Validated} + {@code @NotNull} 做启动期校验。</li>
 *   <li><b>IDE 支持</b>：配合 {@code spring-boot-configuration-processor}
 *       能在 yml 里给出补全提示。</li>
 * </ul>
 *
 * <p><b>为什么放在 {@code common} 包</b>：JWT 是跨模块的基础设施——
 * 用户模块签发它，管理后台模块校验它。放在任何一个业务模块里都会造成反向依赖。
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /**
     * 签名密钥，Base64 编码。
     *
     * <p><b>生成的正确方式</b>：
     * <pre>
     *   # Linux / macOS / Git Bash
     *   head -c 64 /dev/urandom | base64
     *
     *   # 或者用 Java
     *   java -e 'byte[] b = new byte[64]; new SecureRandom().nextBytes(b); \
     *            System.out.println(Base64.getEncoder().encodeToString(b));'
     * </pre>
     *
     * <p><b>三个必须遵守的约束</b>：
     * <ol>
     *   <li><b>必须随机</b>。用 {@code "secret"}、{@code "gymlog123"} 这类可猜字符串，
     *       攻击者能直接伪造任意用户的 token——等于没有认证。</li>
     *   <li><b>长度 ≥ 32 字节</b>。HS256 要求 256 位密钥，短了 jjwt 会直接抛异常。
     *       这里生成 64 字节（512 位）留有余量。</li>
     *   <li><b>绝不提交到 git</b>。放在 {@code application-dev.yml}（已忽略），
     *       生产环境用环境变量 {@code JWT_SECRET} 覆盖。</li>
     * </ol>
     */
    private String secret;

    /**
     * access token 有效期。
     *
     * <p>短——因为它每次请求都要带上，暴露面大。哪怕泄露，攻击窗口也有限。
     * 配合 refresh token 使用，用户不会频繁重新登录。
     */
    private Duration accessTokenTtl = Duration.ofHours(1);

    /** refresh token 有效期。长——用于换取新的 access token，不随请求传输，暴露面小 */
    private Duration refreshTokenTtl = Duration.ofDays(30);
}
