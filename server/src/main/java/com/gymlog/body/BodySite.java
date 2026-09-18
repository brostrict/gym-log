package com.gymlog.body;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 测量部位 / 肌群。
 *
 * <p><b>一个枚举服务两类指标，靠 {@code metricType} 消歧。</b>
 * 围度用 12 个身体部位，酸痛度用 6 个肌群，而 <b>CHEST 两边都有</b>——
 * 「胸围 102cm」和「胸肌酸痛 3 分」是同一个 site 值、不同的 metric_type。
 * 这不是冲突：唯一键是 {@code (user, metric_type, site, measured_at)}，
 * metric_type 本来就参与消歧，两个指标的数据在任何查询里也不会相遇。
 *
 * <p>所以标签取值也要带上 metric_type（「胸围」vs「胸」），
 * 见 {@link BodyMetricType#labelOf(BodySite)}。
 *
 * <h3>⚠️ 为什么有 NONE 而不是用 null</h3>
 *
 * <p>12 个指标（体重、体脂、BMR……）根本没有「部位」这个概念。
 * 如果让 {@code site} 列可空，{@code UNIQUE KEY (user_id, metric_type, site, measured_at)}
 * 就会**对这 12 个指标失效**——MySQL 的唯一索引把 NULL 当作互不相等，
 * 同一时刻的体重可以插进去任意多条，而且不报错。幂等直接没了。
 *
 * <p>所以 {@code site} 是 NOT NULL，用 {@link #NONE} 表示「没有部位」。
 * 选 NONE 而不是空串，是因为本项目所有枚举都按 MyBatis 默认的
 * {@code name()} 存库（{@code SetType} 存的就是 {@code 'WARMUP'}），
 * NONE 自然落进这套约定，不用为「空串 ↔ null」另写一个 TypeHandler。
 */
@Getter
public enum BodySite {

    /** 该指标没有部位概念。**不是**「用户没填」——围度不填部位是会被拒绝的 */
    NONE(""),

    // ---------- 围度的 12 个部位（REQUIREMENTS M6-A-2）----------
    NECK("颈"),
    CHEST("胸"),
    WAIST("腰"),
    HIP("臀"),
    LEFT_UPPER_ARM("左上臂"),
    RIGHT_UPPER_ARM("右上臂"),
    LEFT_FOREARM("左前臂"),
    RIGHT_FOREARM("右前臂"),
    LEFT_THIGH("左大腿"),
    RIGHT_THIGH("右大腿"),
    LEFT_CALF("左小腿"),
    RIGHT_CALF("右小腿"),

    // ---------- 酸痛度的 6 个肌群 ----------
    //
    // 刻意和 MuscleGroup 的 6 个分类一致（胸/背/腿/肩/手臂/核心）。
    // 酸痛度要和训练量对照着看——「这周胸练了 12 组，所以胸最酸」——
    // 两边的分类不一样的话，用户得自己在脑子里做映射。
    // CHEST 与上面共用（靠 metric_type 区分）。
    BACK("背"),
    LEGS("腿"),
    SHOULDERS("肩"),
    ARMS("手臂"),
    CORE("核心");

    /** 显示名。NONE 是空串——它不该出现在任何界面上 */
    private final String label;

    BodySite(String label) {
        this.label = label;
    }

    // ==================================================================
    // 两个集合：谁是围度部位、谁是肌群
    // ==================================================================
    //
    // ⚠️ **这两个集合必须和 BodyMetricType 里的用法一致**，
    // 而且它们只在这里定义一次。写第二遍（比如在 Service 里再列一遍）
    // 就会出现「界面上有 12 个切换器、后端只认 9 个」这类问题——
    // 录得进去、看不到，两边都不报错。

    /** 围度部位：四肢左右分开，共 12 处 */
    public static final Set<BodySite> CIRCUMFERENCE_SITES = Collections.unmodifiableSet(
            new LinkedHashSet<>(java.util.List.of(
                    NECK, CHEST, WAIST, HIP,
                    LEFT_UPPER_ARM, RIGHT_UPPER_ARM,
                    LEFT_FOREARM, RIGHT_FOREARM,
                    LEFT_THIGH, RIGHT_THIGH,
                    LEFT_CALF, RIGHT_CALF)));

    /** 酸痛肌群：6 个粗分类 */
    public static final Set<BodySite> SORENESS_SITES = Collections.unmodifiableSet(
            new LinkedHashSet<>(java.util.List.of(
                    CHEST, BACK, LEGS, SHOULDERS, ARMS, CORE)));

    /** 是不是四肢（左右成对）。只有成对的部位才谈得上「不对称度」（METRICS 2.2） */
    public boolean isPaired() {
        return switch (this) {
            case LEFT_UPPER_ARM, RIGHT_UPPER_ARM,
                 LEFT_FOREARM, RIGHT_FOREARM,
                 LEFT_THIGH, RIGHT_THIGH,
                 LEFT_CALF, RIGHT_CALF -> true;
            default -> false;
        };
    }

    /** 成对部位的左侧/右侧配对。非成对部位返回 null */
    public BodySite opposite() {
        return switch (this) {
            case LEFT_UPPER_ARM -> RIGHT_UPPER_ARM;
            case RIGHT_UPPER_ARM -> LEFT_UPPER_ARM;
            case LEFT_FOREARM -> RIGHT_FOREARM;
            case RIGHT_FOREARM -> LEFT_FOREARM;
            case LEFT_THIGH -> RIGHT_THIGH;
            case RIGHT_THIGH -> LEFT_THIGH;
            case LEFT_CALF -> RIGHT_CALF;
            case RIGHT_CALF -> LEFT_CALF;
            default -> null;
        };
    }
}
