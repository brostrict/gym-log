package com.gymlog.user.dto;

import com.gymlog.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户资料响应。
 *
 * <p><b>为什么不直接返回 {@code User} 实体</b>：实体里有三个不该外泄的字段——
 * {@code passwordHash}（密码哈希）、{@code deleted}（逻辑删除标记）、
 * {@code providerUserId}（第三方平台账号标识）。
 *
 * <p>实体上加 {@code @JsonIgnore} 只能挡住序列化，挡不住「有人往实体里加了新字段
 * 却忘了加注解」。用一个明确的 DTO **逐字段列出允许外泄的内容**，
 * 新增字段时必须主动决定要不要暴露——这是「白名单」而非「黑名单」的思路。
 */
public record UserProfileResponse(

        Long id,
        String email,
        String nickname,

        /** 性别：0=未设置，1=男，2=女 */
        Integer gender,

        Integer birthYear,

        /** 身高（厘米） */
        BigDecimal heightCm,

        /** 训练目标：MUSCLE_GAIN / FAT_LOSS / STRENGTH / GENERAL */
        String goal,

        /** 训练经验：BEGINNER / INTERMEDIATE / ADVANCED */
        String experience,

        /** 单位偏好：kg / lb */
        String unitPref,

        LocalDateTime createdAt

) {

    /**
     * 从实体转换。
     *
     * <p>转换逻辑放在 DTO 里（而不是 Service 里），是为了让「实体→DTO 的映射规则」
     * 和 DTO 的定义待在一起——改了 DTO 字段，这里就是唯一需要同步修改的地方。
     */
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getGender(),
                user.getBirthYear(),
                user.getHeightCm(),
                user.getGoal(),
                user.getExperience(),
                user.getUnitPref(),
                user.getCreatedAt()
        );
    }
}
