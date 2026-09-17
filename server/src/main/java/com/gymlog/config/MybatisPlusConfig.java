package com.gymlog.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p><b>{@code @Configuration} + {@code @Bean} 是什么</b>：
 * Spring 启动时扫描到 {@code @Configuration} 类，会执行里面所有
 * {@code @Bean} 方法，把返回值注册成容器管理的对象。
 *
 * <p>方法名（{@code mybatisPlusInterceptor}）就是 Bean 的名字。
 * 同一个类型有多个 Bean 时，靠名字或 {@code @Qualifier} 区分。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册分页插件。
     *
     * <p><b>为什么必须显式注册</b>：MyBatis-Plus 的分页不是「免费」的。
     * 不注册这个插件的话，调用 {@code selectPage()} 会**返回全部数据**，
     * 然后由 MyBatis-Plus 在内存里截取——数据量大时直接把服务打挂。
     *
     * <p>注册后，分页插件会在 SQL 执行前改写语句，真正带上
     * {@code LIMIT ?, ?}，由数据库完成分页。
     *
     * <p><b>{@code DbType.MYSQL} 的作用</b>：不同数据库的分页语法不同
     * （MySQL 用 {@code LIMIT}，Oracle 用 {@code ROWNUM}，
     * SQL Server 用 {@code OFFSET ... FETCH}）。指定方言后，
     * 插件才知道该生成哪种 SQL。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
