package com.gymlog.common;

import com.gymlog.exercise.dto.ExerciseQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 未知查询参数拦截。
 *
 * <p>这个测试守的是一个**很容易被无意破坏**的行为：
 * 只要有人为了「让某个客户端能用」把校验放宽，静默返回全量数据的坑就回来了。
 */
class QueryParamGuardTest {

    @Test
    @DisplayName("合法参数全部通过")
    void allowsKnownParams() {
        assertThatCode(() -> QueryParamGuard.rejectUnknown(
                ExerciseQuery.class,
                List.of("primaryMuscle", "equipment", "movementPattern",
                        "metricType", "keyword", "onlyCustom", "page", "size")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("没有参数时通过")
    void allowsNoParams() {
        assertThatCode(() -> QueryParamGuard.rejectUnknown(ExerciseQuery.class, List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("拼错的参数名被拒绝，且错误信息列出可用参数")
    void rejectsTypo() {
        // 这是真事：步骤 2.16 验收时照着响应字段名写了 movementPattern，
        // 当时字段叫 pattern，参数被静默忽略，拿到 90 条全量数据还以为筛选没配好
        assertThatThrownBy(() -> QueryParamGuard.rejectUnknown(
                ExerciseQuery.class, List.of("movementPattern", "limt")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("movementPattern")
                .hasMessageContaining("limt")
                // 可用参数要列出来，拼错的人才知道该写什么
                .hasMessageContaining("primaryMuscle")
                .hasMessageContaining("size");
    }

    @Test
    @DisplayName("合法参数名由 DTO 字段推导，加了字段自动生效")
    void derivesAllowedParamsFromDto() {
        Set<String> allowed = QueryParamGuard.allowedParams(ExerciseQuery.class);

        // 这几条覆盖了「新增筛选字段后忘了更新白名单」的情况：
        // 白名单是从类上反射来的，不可能漂移。
        // 如果有人改成手写常量列表，这个测试会红。
        assertThat(allowed).containsExactlyInAnyOrder(
                "primaryMuscle", "equipment", "movementPattern",
                "metricType", "keyword", "onlyCustom", "page", "size");
    }
}
