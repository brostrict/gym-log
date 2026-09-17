package com.gymlog.user;

import com.gymlog.common.Result;
import com.gymlog.user.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户资料接口。
 *
 * <p>这些接口**需要登录**——{@code SecurityConfig} 里的
 * {@code anyRequest().authenticated()} 覆盖了它们
 * （{@code /api/v1/users/**} 不在公开路径列表里）。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户的资料。
     *
     * <p><b>{@code @AuthenticationPrincipal} 是什么</b>：
     * 它从 {@code SecurityContextHolder} 里取出 Authentication 对象的
     * {@code principal} 字段，注入到这个参数上。
     *
     * <p>我们步骤 1.7 的过滤器放进去的 principal 就是 {@code Long userId}，
     * 所以这里能直接拿到。等价于手写：
     * <pre>
     *   Long userId = (Long) SecurityContextHolder.getContext()
     *                          .getAuthentication().getPrincipal();
     * </pre>
     *
     * <p><b>为什么推荐用注解而不是手写</b>：
     * <ul>
     *   <li>少一行样板代码，且不会忘记判空</li>
     *   <li>参数上有类型声明，读代码时一眼看出「这个接口需要登录」</li>
     *   <li>将来把 principal 从 {@code Long} 换成 {@code UserDetails} 时，
     *       只改注解的类型，不用改方法体</li>
     * </ul>
     *
     * <p><b>注意</b>：能走到这个方法体，说明请求**已经通过认证**——
     * {@code userId} 不可能是 null。这是 SecurityConfig 的规则保证的，
     * 所以这里不需要判空。
     */
    @GetMapping("/me")
    public Result<UserProfileResponse> me(@AuthenticationPrincipal Long userId) {
        return Result.ok(userService.getProfile(userId));
    }
}
