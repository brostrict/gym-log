package com.gymlog.exercise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动作分类枚举。
 *
 * <p>这个枚举里混了两种东西：6 个**肌群**和 2 个**非肌群分类**（热身 / 拉伸）。
 * 测试要守住的就是这条界线——它一旦破，拉伸会出现在「肌群周组数」平衡图里，
 * 和「10–20 组/周」的增肌参考区间并列，而那个错误**不会以任何形式报出来**。
 */
class MuscleGroupTest {

    @Test
    @DisplayName("★ isMuscle 把热身和拉伸挡在外面")
    void warmupAndStretchAreNotMuscles() {
        assertThat(MuscleGroup.WARMUP.isMuscle()).isFalse();
        assertThat(MuscleGroup.STRETCH.isMuscle()).isFalse();

        for (MuscleGroup m : List.of(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.LEGS,
                MuscleGroup.SHOULDERS, MuscleGroup.ARMS, MuscleGroup.CORE)) {
            assertThat(m.isMuscle()).as("%s 是肌群", m).isTrue();
        }
    }

    @Test
    @DisplayName("★ muscles() 只返回 6 个 —— 统计聚合靠它，不是 values()")
    void musclesExcludesNonMuscles() {
        assertThat(MuscleGroup.muscles())
                .hasSize(6)
                .doesNotContain(MuscleGroup.WARMUP, MuscleGroup.STRETCH)
                .containsExactly(MuscleGroup.CHEST, MuscleGroup.BACK, MuscleGroup.LEGS,
                        MuscleGroup.SHOULDERS, MuscleGroup.ARMS, MuscleGroup.CORE);
    }

    @Test
    @DisplayName("每个分类都有中文名（包括两个非肌群——它们要显示在动作库里）")
    void allHaveDisplayName() {
        for (MuscleGroup m : MuscleGroup.values()) {
            assertThat(m.getDisplayName()).as("%s 的中文名", m).isNotBlank();
        }
    }

    @Test
    @DisplayName("非肌群分类恰好是 2 个")
    void exactlyTwoNonMuscles() {
        long nonMuscles = Arrays.stream(MuscleGroup.values())
                .filter(m -> !m.isMuscle())
                .count();
        assertThat(nonMuscles)
                .as("多一个非肌群分类，就多一处可能漏过滤的统计")
                .isEqualTo(2);
    }
}
