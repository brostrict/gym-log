package com.gymlog.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联，对应 {@code user_role} 表。
 *
 * <p>这是典型的**多对多中间表**：一个用户可以有多个角色，
 * 一个角色可以授予多个用户。
 *
 * <p><b>为什么不做成 {@code user.role_id} 外键</b>：
 * 那样一个用户只能有一个角色。而现实中「既是教练又是管理员」
 * 这种组合很常见——多对多才表达得了。
 */
@Data
@TableName("user_role")
public class UserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long roleId;

    private LocalDateTime createdAt;
}
