package com.gymlog.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色，对应 {@code role} 表。
 *
 * <p>角色是「权限的集合」——它本身不表达能力，能力由
 * {@code role_permission} 关联的权限项定义。
 *
 * <p><b>为什么要多这一层，不直接把权限授给用户</b>：
 * 想象一个有 5000 用户、14 个权限的系统。直接授权意味着
 * 「给所有管理员加上 audit:read」要执行 5000 条 INSERT；
 * 而通过角色只需改一条 {@code role_permission}。
 * 角色本质上是**权限的分组与复用机制**。
 */
@Data
@TableName("role")
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色编码，如 {@code USER} / {@code ADMIN}。
     *
     * <p><b>代码里引用它，不要引用 {@code name}</b>——
     * name 是给人看的（「普通用户」），随时可能改；
     * code 是契约，改了会让代码里的 {@code hasRole("ADMIN")} 失效。
     */
    private String code;

    /** 角色名称，仅用于界面显示 */
    private String name;

    private String description;

    private LocalDateTime createdAt;

    // ---------- 内置角色编码常量 ----------
    // 常量放这里，避免代码各处硬编码字符串——写错了编译期就能发现

    public static final String CODE_USER = "USER";
    public static final String CODE_ADMIN = "ADMIN";
}
