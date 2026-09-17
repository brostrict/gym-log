package com.gymlog.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库的 {@code user} 表。
 *
 * <p><b>字段名怎么映射的</b>：数据库是 snake_case（{@code password_hash}），
 * Java 是 camelCase（{@code passwordHash}）。这个转换由
 * {@code mybatis-plus.configuration.map-underscore-to-camel-case: true} 自动完成，
 * 所以不需要在每个字段上写 {@code @TableField("password_hash")}。
 *
 * <p><b>只写了两个注解</b>：{@code @TableName} 和 {@code @TableId}。
 * 其余字段靠约定映射——这就是 MyBatis-Plus 的「约定优于配置」。
 */
@Data
@TableName("user")
public class User {

    /**
     * 主键。
     *
     * <p>{@code IdType.AUTO} 表示用数据库的自增主键
     * （对应建表语句里的 {@code AUTO_INCREMENT}），
     * 插入后 MyBatis-Plus 会把生成的主键回填到这个对象里。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    // ==================== 账号 ====================

    private String email;

    /**
     * BCrypt 密码哈希。
     *
     * <p><b>两道防线，防止哈希泄露</b>：
     * <ol>
     *   <li>{@code @JsonIgnore}：Jackson 序列化时跳过这个字段。
     *       即使某天有人图省事直接 {@code return Result.ok(user)}，
     *       密码哈希也不会出现在 HTTP 响应里。</li>
     *   <li>{@code @ToString.Exclude}：Lombok 生成的 {@code toString()} 不含它。
     *       否则一句 {@code log.info("user={}", user)} 就会把哈希写进日志文件——
     *       而日志通常比数据库更容易被看到（运维、日志平台、误提交的日志文件）。</li>
     * </ol>
     *
     * <p><b>为什么这很重要</b>：BCrypt 哈希是可以离线暴力破解的。
     * 攻击者拿到哈希 + 盐，就可以在自己机器上无限次尝试，
     * 不受登录接口限流的约束。拿到哈希 ≈ 拿到一份可以慢慢猜的密码。
     */
    @JsonIgnore
    @ToString.Exclude
    private String passwordHash;

    private String nickname;

    /** 账号状态：{@link #STATUS_NORMAL} 正常 / {@link #STATUS_DISABLED} 禁用 */
    private Integer status;

    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DISABLED = 0;

    // ==================== 个人资料 ====================

    /** 性别：0=未设置，1=男，2=女 */
    private Integer gender;

    public static final int GENDER_UNSET = 0;
    public static final int GENDER_MALE = 1;
    public static final int GENDER_FEMALE = 2;

    private Integer birthYear;

    /** 身高（厘米）。用 BigDecimal 而不是 double——小数运算不能用二进制浮点 */
    private BigDecimal heightCm;

    /** 训练目标：MUSCLE_GAIN / FAT_LOSS / STRENGTH / GENERAL */
    private String goal;

    /** 训练经验：BEGINNER / INTERMEDIATE / ADVANCED */
    private String experience;

    /** 单位偏好：kg / lb。注意库里数值一律存 kg，这个字段只影响显示 */
    private String unitPref;

    // ==================== 预留：第三方登录 ====================

    /** 登录方式：local / wechat / apple */
    private String provider;

    private String providerUserId;

    // ==================== 审计字段 ====================

    private Integer emailVerified;

    private LocalDateTime lastLoginAt;

    /**
     * 创建时间。
     *
     * <p>不需要在代码里赋值——建表时写了
     * {@code DEFAULT CURRENT_TIMESTAMP}，数据库会自动填。
     * 而且 MyBatis-Plus 插入时会跳过 null 字段，不会把这个默认值覆盖成 null。
     */
    private LocalDateTime createdAt;

    /** 更新时间。同样由数据库的 {@code ON UPDATE CURRENT_TIMESTAMP} 维护 */
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除标记：0=未删，1=已删。
     *
     * <p>字段名 {@code deleted} 已在 application.yml 里通过
     * {@code mybatis-plus.global-config.db-config.logic-delete-field} 全局声明，
     * 所以这里不需要再加 {@code @TableLogic} 注解。
     *
     * <p>效果：调 {@code deleteById()} 实际执行的是
     * {@code UPDATE user SET deleted=1 WHERE id=?}；
     * 所有查询自动附加 {@code WHERE deleted=0}。
     */
    private Integer deleted;
}
