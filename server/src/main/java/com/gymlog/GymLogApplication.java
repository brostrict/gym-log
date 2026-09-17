package com.gymlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * gym-log 后端启动类。
 *
 * <p>{@code @SpringBootApplication} 是一个组合注解，等价于下面三个：
 * <ul>
 *   <li>{@code @SpringBootConfiguration} —— 声明这是一个配置类</li>
 *   <li>{@code @EnableAutoConfiguration} —— 开启自动配置。
 *       Spring Boot 会扫描 classpath，发现有哪些依赖，就自动装配对应的组件。
 *       例如加了 spring-boot-starter-web，它就自动配好 Tomcat 和 Spring MVC。</li>
 *   <li>{@code @ComponentScan} —— 扫描当前包及其子包下的
 *       {@code @Component} / {@code @Service} / {@code @Mapper} 等注解</li>
 * </ul>
 *
 * <p><b>注意启动类所在的包位置</b>：{@code com.gymlog} 是根包，
 * 所有业务代码都必须放在它下面（如 {@code com.gymlog.user}），
 * 否则 {@code @ComponentScan} 扫不到。
 */
@SpringBootApplication
public class GymLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(GymLogApplication.class, args);
    }
}
