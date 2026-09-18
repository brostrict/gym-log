package com.gymlog.training.dto;

import com.gymlog.training.SetRecord;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 记录一组之后的返回 —— 只带**这个动作现在的进度**，
 * 不带整份会话。
 *
 * <h3>为什么不返回整个会话详情</h3>
 *
 * <p>跟练时每 2-3 分钟记一组。返回整份会话意味着每次都要
 * 重新查一遍所有动作和所有组目标（一个会话 5 个动作 × 4 组 = 20 多条），
 * 而客户端真正需要的只有：
 *
 * <ul>
 *   <li>「这个动作现在 3/5 组了」——用于更新进度条</li>
 *   <li>「这个动作完成了没」——用于自动跳到下一个动作</li>
 * </ul>
 *
 * <p>这几个数字就是整个响应体的全部内容。
 */
public record SetRecordResponse(

        Long sessionExerciseId,

        /** 动作状态：PENDING / IN_PROGRESS / COMPLETED / SKIPPED */
        String status,
        String statusLabel,

        /** 已记录的组数 */
        int recordedSets,

        /** 计划的组数。用户临时加组时会超过它 */
        int targetSets,

        /** 是否已结束（完成或跳过）——客户端据此自动跳到下一个动作 */
        boolean finished

) {

    /**
     * 一整份会话的组记录 —— 给详情接口用。
     *
     * <p>和 {@link SetRecordResponse} 分开是因为用途不同：
     * 这个是「回看这次训练」，那个是「我刚记了一组，更新界面」。
     */
    public record Item(
            Long id,
            Integer setNumber,
            String setType,
            String setTypeLabel,
            BigDecimal weight,
            Integer reps,
            Integer durationSec,
            BigDecimal distanceM,
            BigDecimal rpe,
            Integer restActualSec,
            String note,
            LocalDateTime completedAt,
            /** 是否计入容量（热身组不计） */
            boolean countsTowardVolume
    ) {
        public static Item from(SetRecord r) {
            return new Item(
                    r.getId(),
                    r.getSetNumber(),
                    r.getSetType() == null ? null : r.getSetType().name(),
                    r.getSetType() == null ? null : r.getSetType().getDisplayName(),
                    r.getWeight(),
                    r.getReps(),
                    r.getDurationSec(),
                    r.getDistanceM(),
                    r.getRpe(),
                    r.getRestActualSec(),
                    r.getNote(),
                    r.getCompletedAt(),
                    r.countsTowardVolume());
        }

        public static List<Item> from(List<SetRecord> records) {
            return records.stream().map(Item::from).toList();
        }
    }
}
