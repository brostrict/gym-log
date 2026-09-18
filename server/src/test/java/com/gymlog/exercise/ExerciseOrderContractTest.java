package com.gymlog.exercise;

import com.gymlog.exercise.dto.ExerciseQuery;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.gymlog.exercise.dto.ExerciseResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 动作库排序的**契约测试**。
 *
 * <h3>为什么需要它</h3>
 *
 * <p>动作列表要按肌群分组显示，而「肌群的先后」定义在 {@link MuscleGroup}
 * 的声明顺序里（胸 → 背 → 腿 → 肩 → 手臂 → 核心 → 热身 → 拉伸）。
 *
 * <p>但 MyBatis-Plus 的 wrapper 表达不了「按自定义顺序排」，
 * 只能在 SQL 里写 {@code ORDER BY FIELD(primary_muscle, 'CHEST','BACK',...)}。
 * **这是枚举顺序在 SQL 里的第二份**，而两份不一致时：
 *
 * <ul>
 *   <li>不报错</li>
 *   <li>不崩</li>
 *   <li>只是动作库的顺序变得莫名其妙——比如「手臂」排在最前</li>
 * </ul>
 *
 * <p>所以这里断言「查询结果里肌群的出现顺序 = 枚举的声明顺序」。
 * 改枚举顺序而忘了改 SQL，这条会红。
 *
 * <p>（同类先例：{@code SessionSummaryServiceTest.sqlE1rmMatchesJavaE1rm}
 * 守着 SQL 里那份 e1RM 公式。）
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class ExerciseOrderContractTest {

    @Autowired private ExerciseService exerciseService;

    @Test
    @DisplayName("★ 动作列表里的肌群顺序 = MuscleGroup 的声明顺序（SQL 的 FIELD() 要和枚举一致）")
    void muscleOrderMatchesEnumDeclarationOrder() {
        IPage<ExerciseResponse> page = exerciseService.query(1L, new ExerciseQuery());

        // 把结果里肌群**首次出现**的顺序抽出来
        List<String> appearanceOrder = new ArrayList<>();
        for (ExerciseResponse e : page.getRecords()) {
            if (appearanceOrder.isEmpty()
                    || !appearanceOrder.get(appearanceOrder.size() - 1).equals(e.primaryMuscle())) {
                if (!appearanceOrder.contains(e.primaryMuscle())) {
                    appearanceOrder.add(e.primaryMuscle());
                }
            }
        }

        // 期望顺序 = 枚举里**实际有动作的**那些分类，按声明顺序
        List<String> expected = java.util.Arrays.stream(MuscleGroup.values())
                .map(Enum::name)
                .filter(appearanceOrder::contains)
                .toList();

        assertThat(appearanceOrder)
                .as("肌群在列表里的出现顺序必须和 MuscleGroup 的声明顺序一致。"
                    + "不一致通常意味着 MuscleGroup 加了新分类，"
                    + "但 ExerciseService.muscleOrderClause() 的 FIELD() 列表没跟着改")
                .containsExactlyElementsOf(expected);
    }

    @Test
    @DisplayName("★ 请求上限条数时必须能拿到**全部**动作 —— 否则界面上会静默少几个")
    void maxPageSizeReturnsWholeLibrary() {
        ExerciseQuery q = new ExerciseQuery();
        q.setSize(500);            // 服务端上限
        IPage<ExerciseResponse> page = exerciseService.query(1L, q);

        // ⚠️ 断言的是「拿到的条数 == 总数」，不是「大于等于某个数」。
        //
        // 动作库还会继续长。等到它超过上限的那一天，任何客户端都会
        // 静默少拿几个——列表看起来完全正常，只是少了。这条断言在那天变红，
        // 逼着做客户端分页（或重新决定上限），而不是让截断悄悄发生。
        assertThat(page.getRecords().size())
                .as("请求到上限了却拿不全 —— 该做分页了，不要再抬上限")
                .isEqualTo((int) page.getTotal());
        assertThat(page.getTotal()).isGreaterThanOrEqualTo(125);
    }

    @Test
    @DisplayName("超过上限的 size 被夹到上限，而不是照单全收")
    void oversizedRequestIsCapped() {
        ExerciseQuery q = new ExerciseQuery();
        q.setSize(1_000_000);
        // 防的是「一次把整张表拉走」——那是性能问题也是数据泄露风险。
        // 上限从 100 提到 500 是因为动作库到了 125 个，
        // 不是因为这条防护不必要了。
        assertThat(q.normalizedSize()).isEqualTo(500);
    }

    @Test
    @DisplayName("热身和拉伸能被查到 —— 它们不是肌群，但要能在动作库里筛出来")
    void warmupAndStretchAreQueryable() {
        ExerciseQuery warmup = new ExerciseQuery();
        warmup.setPrimaryMuscle(MuscleGroup.WARMUP);
        assertThat(exerciseService.query(1L, warmup).getRecords()).isNotEmpty();

        ExerciseQuery stretch = new ExerciseQuery();
        stretch.setPrimaryMuscle(MuscleGroup.STRETCH);
        assertThat(exerciseService.query(1L, stretch).getRecords()).isNotEmpty();
    }
}
