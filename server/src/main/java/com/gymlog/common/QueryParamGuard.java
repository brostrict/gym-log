package com.gymlog.common;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 拒绝「不认识的查询参数」。
 *
 * <h3>为什么需要这个</h3>
 *
 * <p>Spring 把查询参数绑定到 POJO 时，默认**静默忽略**对象上没有的字段。
 * 对筛选接口来说这个默认值很危险：
 *
 * <pre>
 *   ?movementPattern=HORIZONTAL_PUSH     ← 字段当时叫 pattern
 *   → 参数被忽略
 *   → 返回全部 90 条，HTTP 200，code=0
 *   → 客户端以为筛选生效了，拿到的是全量数据
 * </pre>
 *
 * <p><b>没有报错，数据也「合法」，只是不是用户要的。</b>
 * 服务端日志里一切正常，客户端要排查很久。
 *
 * <p>步骤 2.16 验收时我自己踩了这个坑（照着响应字段名写了参数），
 * 所以加了这道闸。
 *
 * <h3>⚠️ 为什么不用 {@code WebDataBinder.setIgnoreUnknownFields(false)}</h3>
 *
 * <p>试过，**每个请求都会 500**。Spring MVC 绑定 {@code @ModelAttribute} 时
 * 会把 **HTTP 请求头**也当成待绑定属性，关掉「忽略未知字段」后，
 * {@code Accept-Encoding} / {@code User-Agent} 全变成非法属性：
 *
 * <pre>
 *   NotWritablePropertyException: Invalid property 'acceptencoding'
 *   of bean class [com.gymlog.exercise.dto.ExerciseQuery]
 * </pre>
 *
 * <p>框架没有「只对查询参数严格、忽略请求头」的开关，所以自己查。
 * 二十行代码，而且错误信息能做得比框架清楚（直接列出可用参数）。
 */
public final class QueryParamGuard {

    private QueryParamGuard() {
    }

    /**
     * 合法参数名 —— **由查询对象的字段自动推导**。
     *
     * <p>不手写常量列表：手写的迟早会和 DTO 漂移，
     * 而漂移的表现是「加了新筛选条件但参数被拒」，排查起来莫名其妙。
     * 从类上推导则永远不会不一致。
     *
     * <p>（{@code getDeclaredFields} 对 Lombok 生成的类同样有效——
     * Lombok 只生成 getter/setter，字段本身是真实存在的。）
     */
    public static Set<String> allowedParams(Class<?> queryType) {
        return Arrays.stream(queryType.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * 参数名里有不认识的就直接抛 400。全都认识则什么也不做。
     *
     * @param queryType        查询对象的类型，用它推导合法参数名
     * @param actualParamNames 请求里实际出现的参数名
     */
    public static void rejectUnknown(Class<?> queryType, Collection<String> actualParamNames) {
        Set<String> allowed = allowedParams(queryType);

        List<String> unknown = new ArrayList<>();
        for (String name : actualParamNames) {
            if (!allowed.contains(name)) {
                unknown.add(name);
            }
        }
        if (unknown.isEmpty()) {
            return;
        }

        // 报错时把可用参数一并列出——拼错的人一眼就知道该写什么，
        // 不用去翻 Swagger
        throw new BizException(ErrorCode.BAD_REQUEST, String.format(
                "不认识的查询参数：%s。可用参数：%s",
                String.join("、", unknown),
                String.join("、", new TreeSet<>(allowed))));
    }
}
