package com.gymlog.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问接口。
 *
 * <p><b>注意：这是一个接口，没有任何实现类。</b>
 * 实现由 MyBatis 在启动时动态生成（JDK 动态代理），
 * 所以直接 {@code @Autowired} 注入就能用。
 *
 * <p><b>{@code @Mapper} 注解的作用</b>：告诉 MyBatis
 * 「扫描到这个接口时，为它生成一个代理实现并注册成 Spring Bean」。
 * 没有这个注解，启动时会报
 * {@code No qualifying bean of type 'UserMapper'}。
 *
 * <p><b>继承 {@code BaseMapper<User>} 白拿了哪些方法</b>：
 * <pre>
 *   insert(entity)              插入
 *   deleteById(id)              按主键删除（逻辑删除 → UPDATE deleted=1）
 *   updateById(entity)          按主键更新（null 字段不更新）
 *   selectById(id)              按主键查询
 *   selectList(wrapper)         条件查询
 *   selectPage(page, wrapper)   分页查询（需要配置分页插件）
 *   selectCount(wrapper)        统计
 *   exists(wrapper)             是否存在
 * </pre>
 *
 * <p><b>需要自己写的方法</b>：BaseMapper 覆盖不到的场景，比如多表 JOIN、
 * 复杂的聚合统计。本项目里指标聚合 SQL（Phase 4）会用到。
 * 那时有两种写法：
 * <ul>
 *   <li>注解式：{@code @Select("SELECT ...")} —— 适合短 SQL</li>
 *   <li>XML 式：{@code resources/mapper/UserMapper.xml} —— 适合长 SQL、动态条件</li>
 * </ul>
 * 本项目 Phase 4 的聚合查询会走 XML。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 目前 BaseMapper 提供的方法已经够用。
    // 按邮箱查询会先用 LambdaQueryWrapper 实现，见步骤 1.5。
}
