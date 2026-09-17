package com.gymlog.common;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应体。
 *
 * <p>配合 {@link Result} 使用，最终响应形如：
 * <pre>
 * {
 *   "code": 0,
 *   "message": "成功",
 *   "data": {
 *     "records": [ ... ],
 *     "total": 90,
 *     "page": 1,
 *     "size": 20,
 *     "pages": 5,
 *     "hasNext": true
 *   }
 * }
 * </pre>
 *
 * <p><b>为什么不直接把 MyBatis-Plus 的 {@code IPage} 序列化返回</b>：
 * {@code IPage} 的实现类（{@code Page}）里有一堆内部字段——
 * {@code optimizeCountSql}、{@code searchCount}、{@code maxLimit}、
 * {@code orders} 等等，全是分页插件的工作参数，跟客户端毫无关系。
 * 直接序列化会把这些内部细节全部暴露出去，且版本升级时字段可能变化，
 * 等于给接口加了一份隐式的、不受控的契约。
 *
 * <p>用这个 DTO 明确列出客户端真正需要的东西：数据 + 分页元信息。
 */
public record PageResponse<T>(

        /** 当前页数据 */
        List<T> records,

        /** 总记录数 */
        long total,

        /** 当前页码，从 1 开始 */
        long page,

        /** 每页条数 */
        long size,

        /** 总页数 */
        long pages,

        /** 是否还有下一页。前端据此决定「加载更多」按钮是否可用 */
        boolean hasNext

) {

    /**
     * 直接包装已转换好的分页结果。
     *
     * <p>用在 Service 层已经做过实体 → DTO 转换的场景
     * （{@code IPage.convert()} 之后）。
     */
    public static <T> PageResponse<T> from(IPage<T> page) {
        return new PageResponse<>(
                page.getRecords(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages(),
                page.getCurrent() < page.getPages()
        );
    }

    /**
     * 从分页对象转换，同时把实体逐条转成 DTO。
     *
     * <p>用在 Service 层返回的是实体的场景。
     *
     * <p><b>为什么提供两个重载</b>：转换发生在 Service 还是 Controller，
     * 取决于该 Service 方法是否还被别处复用。
     * 提供两个入口，调用方按实际情况选，不用为了适配而多绕一层。
     *
     * @param converter 实体 → DTO 的转换函数，通常是 {@code XxxResponse::from}
     */
    public static <E, T> PageResponse<T> from(IPage<E> page, Function<E, T> converter) {
        return new PageResponse<>(
                page.getRecords().stream().map(converter).toList(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages(),
                page.getCurrent() < page.getPages()
        );
    }
}
