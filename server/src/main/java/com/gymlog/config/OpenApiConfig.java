package com.gymlog.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI（Swagger）配置。
 *
 * <p>启动后访问 {@code http://localhost:8080/swagger-ui.html} 即可看到接口文档。
 *
 * <p><b>这份文档是自动生成的</b>——springdoc 在运行期扫描所有
 * {@code @RestController}，根据方法签名和参数注解推导出接口结构。
 * 不需要手写任何文档文件，所以**不存在文档过期的问题**。
 */
@Configuration
public class OpenApiConfig {

    /** 安全方案的名字，会被页面上的「Authorize」按钮引用 */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI gymLogOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("gym-log API")
                        .version("v1")
                        .description("""
                                健身训练记录与身体数据追踪服务的后端接口。

                                **如何调试需要登录的接口**：
                                1. 先调 `POST /api/v1/auth/login` 拿到 `accessToken`
                                2. 点右上角 **Authorize** 按钮，填入 token（不需要加 `Bearer ` 前缀）
                                3. 之后所有请求会自动带上 `Authorization` 头

                                注意 access token 只有 1 小时有效期，
                                过期后用 `POST /api/v1/auth/refresh` 换新的。
                                """)
                        .contact(new Contact().name("brostrict"))
                        .license(new License().name("Private")))

                // ---------- 声明认证方式 ----------
                // 这一步是为了让页面出现「Authorize」按钮。
                // 不配的话，所有需要 token 的接口在页面上都没法直接调试，
                // 只能去命令行用 curl——那就失去了用 Swagger 的意义。
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                // HTTP 认证（区别于 apiKey / oauth2）
                                .type(SecurityScheme.Type.HTTP)
                                // bearer 表示凭据放在 Authorization: Bearer <token>
                                .scheme("bearer")
                                // 仅用于文档展示，提示这是个 JWT
                                .bearerFormat("JWT")
                                .description("填入登录接口返回的 accessToken（不需要加 Bearer 前缀）")))

                // ---------- 全局应用 ----------
                // 声明「所有接口默认需要这个认证」。
                // 公开接口（如 /auth/login）在页面上依然可以不带 token 直接调用——
                // 这一项只影响文档展示，**不做实际拦截**，拦截由 SecurityConfig 负责。
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }
}
