package com.gymlog.program;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymlog.exercise.Exercise;
import com.gymlog.exercise.ExerciseMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证内置计划模板。
 *
 * <p><b>最关键的一条：模板里引用的所有动作名称必须存在于动作库。</b>
 *
 * <p>这是个跨数据的引用完整性问题——模板用**名称**引用动作
 * （因为动作 id 各环境不一致），所以名称写错不会在插入时报错，
 * 而是等到用户点「使用这个模板」时才失败。
 *
 * <p>而且这种错误**在开发时很难发现**：模板 JSON 是手写的，
 * 名称里少一个字、多一个空格，肉眼扫过去看不出来。
 * 必须让机器来核对。
 */
@SpringBootTest
@Transactional
class ProgramTemplateTest {

    @Autowired private ProgramTemplateMapper templateMapper;
    @Autowired private ExerciseMapper exerciseMapper;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @DisplayName("6 个内置模板都已入库且结构可解析")
    void shouldHaveSixPublishedTemplates() throws IOException {
        List<ProgramTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<ProgramTemplate>()
                        .eq(ProgramTemplate::getStatus, ProgramTemplate.STATUS_PUBLISHED)
                        .orderByAsc(ProgramTemplate::getSortOrder));

        assertThat(templates).hasSize(6);

        for (ProgramTemplate t : templates) {
            JsonNode structure = objectMapper.readTree(t.getStructure());

            assertThat(structure.has("weeks"))
                    .as(t.getCode() + " 缺少 weeks").isTrue();
            assertThat(structure.has("days"))
                    .as(t.getCode() + " 缺少 days").isTrue();

            assertThat(structure.get("weeks").size())
                    .as(t.getCode() + " 的周结构为空").isGreaterThan(0);
            assertThat(structure.get("days").size())
                    .as(t.getCode() + " 的训练日为空").isGreaterThan(0);

            // totalWeeks 应和 weeks 数组长度一致（不限期计划除外）
            if (t.getTotalWeeks() != null && t.getTotalWeeks() > 0) {
                assertThat(structure.get("weeks").size())
                        .as(t.getCode() + " 的 totalWeeks 与 weeks 数组长度不一致")
                        .isEqualTo(t.getTotalWeeks());
            }
        }
    }

    @Test
    @DisplayName("★ 模板引用的所有动作名称都存在于动作库")
    void allReferencedExercisesExist() throws IOException {
        // 动作库里所有内置动作的名称
        Set<String> knownNames = new HashSet<>();
        for (Exercise e : exerciseMapper.selectList(
                new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID))) {
            knownNames.add(e.getName());
        }
        assertThat(knownNames).isNotEmpty();

        List<ProgramTemplate> templates = templateMapper.selectList(null);
        List<String> missing = new ArrayList<>();

        for (ProgramTemplate t : templates) {
            JsonNode structure = objectMapper.readTree(t.getStructure());
            for (JsonNode day : structure.get("days")) {
                JsonNode exercises = day.get("exercises");
                if (exercises == null || exercises.isNull()) {
                    continue;
                }
                for (JsonNode ex : exercises) {
                    JsonNode nameNode = ex.get("exerciseName");
                    if (nameNode == null || nameNode.isNull()) {
                        missing.add(t.getCode() + " / " + day.get("name").asText() + " : 缺少 exerciseName 字段");
                        continue;
                    }
                    String name = nameNode.asText();
                    if (!knownNames.contains(name)) {
                        missing.add(t.getCode() + " / " + day.get("name").asText() + " : 「" + name + "」不存在");
                    }
                }
            }
        }

        // 一次性列出所有缺失项，而不是遇到第一个就失败——
        // 修的时候能一次改完，不用反复跑
        assertThat(missing)
                .as("模板引用了动作库里不存在的动作：\n" + String.join("\n", missing))
                .isEmpty();
    }

    @Test
    @DisplayName("模板里的超级组配置合法（要么没有，要么至少 2 个成员）")
    void supersetConfigurationsAreValid() throws IOException {
        List<ProgramTemplate> templates = templateMapper.selectList(null);

        for (ProgramTemplate t : templates) {
            JsonNode structure = objectMapper.readTree(t.getStructure());
            for (JsonNode day : structure.get("days")) {
                JsonNode exercises = day.get("exercises");
                if (exercises == null || exercises.isNull()) {
                    continue;
                }

                // 统计每个超级组的成员数
                var counts = new java.util.HashMap<Integer, Integer>();
                for (JsonNode ex : exercises) {
                    JsonNode group = ex.get("supersetGroup");
                    if (group != null && !group.isNull()) {
                        counts.merge(group.asInt(), 1, Integer::sum);
                    }
                }

                counts.forEach((group, count) ->
                        assertThat(count)
                                .as(t.getCode() + " / " + day.get("name").asText()
                                        + " 的超级组 " + group + " 只有 " + count + " 个动作")
                                .isGreaterThanOrEqualTo(2));
            }
        }
    }
}
