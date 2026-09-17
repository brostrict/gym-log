package com.gymlog.user;

import com.gymlog.common.Result;
import com.gymlog.user.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、刷新、登出。
 *
 * <p>V1 目前只有注册，其余在步骤 1.6 / 1.9 补齐。
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /**
     * 注册新用户。
     *
     * <p><b>{@code @Valid} 的作用</b>：触发 {@link RegisterRequest} 上标注的
     * JSR-303 校验注解。校验失败会抛 {@code MethodArgumentNotValidException}，
     * 被 {@code GlobalExceptionHandler} 捕获并转成 400 响应。
     *
     * <p><b>注意 {@code @Valid} 的位置很重要</b>：必须标在参数上。
     * 如果只标在 DTO 类的字段上而不加 {@code @Valid}，注解形同虚设——
     * 这是新手最常犯的错误之一，接口看起来有校验，实际上什么都不校验。
     *
     * <p><b>返回什么</b>：目前只返回用户 ID。
     * 步骤 1.6 会改成注册成功后直接签发 token（自动登录），
     * 省掉用户注册完还要再登录一次的麻烦。
     *
     * <p><b>为什么不返回 {@code User} 实体</b>：实体里有 {@code passwordHash}
     * 这类不该外泄的字段。虽然 {@code User} 上已经加了 {@code @JsonIgnore} 兜底，
     * 但依赖「记得给每个敏感字段加注解」本身就是风险——
     * 直接返回一个明确的、只有必要字段的对象更安全。
     */
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterRequest request) {
        Long userId = userService.register(request);
        return Result.ok(userId);
    }
}
