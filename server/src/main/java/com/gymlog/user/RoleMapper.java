package com.gymlog.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色数据访问接口。
 *
 * <p>{@code role} 表是**低频变更的字典数据**——只有两个内置角色，
 * 建好之后基本不动。所以这里不做什么缓存优化，
 * 每次注册查一次数据库的开销可以忽略（相对 BCrypt 的 80ms 来说）。
 *
 * <p>如果将来角色变多、查询变频繁，可以加 Spring Cache：
 * {@code @Cacheable("roles")}，并在角色变更时 {@code @CacheEvict}。
 */
@Mapper
public interface RoleMapper extends BaseMapper<Role> {
}
