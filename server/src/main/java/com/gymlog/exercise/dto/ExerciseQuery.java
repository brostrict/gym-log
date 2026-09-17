package com.gymlog.exercise.dto;

import com.gymlog.exercise.Equipment;
import com.gymlog.exercise.MetricType;
import com.gymlog.exercise.MovementPattern;
import com.gymlog.exercise.MuscleGroup;
import lombok.Data;

/**
 * 动作查询条件（GET 请求的查询参数）。
 *
 * <p>所有字段都是可选的——不传就是「不按这个维度筛选」。
 *
 * <p><b>为什么用枚举类型而不是 String 接收</b>：
 * Spring 会自动把请求参数转成枚举。传了非法值（如 {@code muscle=INVALID}）
 * 会抛类型转换异常，被 {@code GlobalExceptionHandler} 转成 400 参数错误。
 * 如果用 String 接收，就得在 Service 里手动解析并处理非法值——
 * 让框架做这件事更省事，且错误处理是统一的。
 */
@Data
public class ExerciseQuery {

    /** 按肌群筛选，如 {@code CHEST} */
    private MuscleGroup muscle;

    /** 按器械筛选，如 {@code DUMBBELL} */
    private Equipment equipment;

    /** 按动作模式筛选，如 {@code HORIZONTAL_PUSH} */
    private MovementPattern pattern;

    /** 按计量类型筛选，如 {@code REPS_ONLY}（找徒手动作时有用） */
    private MetricType metricType;

    /**
     * 关键字，匹配名称或别名。
     *
     * <p>「卧推」能匹配到「杠铃卧推」；「bench」能匹配到别名里含 bench press 的动作。
     */
    private String keyword;

    /** 是否只查自定义动作（我的动作）。不传则查「内置 + 我的」 */
    private Boolean onlyCustom;

    /** 页码，从 1 开始 */
    private Integer page = 1;

    /**
     * 每页条数。
     *
     * <p>上限 100 是**必要的防护**——不限制的话，
     * 客户端传 {@code size=1000000} 就能一次性把整张表拉出来，
     * 既是性能问题也是数据泄露风险（爬虫可以轻松全量抓取）。
     */
    private Integer size = 20;

    // ---------- 归一化 ----------

    public int normalizedPage() {
        return (page == null || page < 1) ? 1 : page;
    }

    public int normalizedSize() {
        if (size == null || size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
