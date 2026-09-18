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
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles({"dev", "test"})
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

    /**
     * ★ 时长类动作的模板必须带 {@code targetDurationSec}，且**不能**再有 reps 字段。
     *
     * <p><b>这条测试是被一次真实的静默失败逼出来的。</b>
     *
     * <p>V14 要修模板 JSON 里「把秒数塞进次数字段」的历史遗留
     * （{@code "note":"目标是秒数"}）。当时用的是最直觉的写法——
     * 拿 V9 源文件里的原文做 {@code REPLACE()}：
     *
     * <pre>
     * REPLACE(structure, '"targetRepsMin":30,"targetRepsMax":60', ...)
     * </pre>
     *
     * <p>而 MySQL 的 JSON 列是二进制格式，读出来时**会重排 key**
     * （按 key 长度、再按字节序），并且渲染时 key 和值之间**带空格**。
     * 实际存的是 {@code "targetRepsMax": 60, "targetRepsMin": 30}——
     * REPLACE 匹配 0 行、**静默成功**，Flyway 报告迁移通过。
     *
     * <p>后果是「从模板建计划 → 没有 targetDurationSec → 跟练页没有倒计时」，
     * 整条链路没有任何一层会报错。
     *
     * <p>所以必须有一条测试盯着「模板 JSON 里的时长动作真的带上了新字段」。
     * 迁移里那道 SQL 断言是第二道防线，这是第一道。
     */
    @Test
    @DisplayName("★ 时长类动作的模板带 targetDurationSec，且不再有 reps 字段")
    void durationExercisesUseTargetDuration() throws IOException {
        // 名称 → 计量类型
        var metricByName = new java.util.HashMap<String, com.gymlog.exercise.MetricType>();
        for (Exercise e : exerciseMapper.selectList(
                new LambdaQueryWrapper<Exercise>()
                        .eq(Exercise::getUserId, Exercise.BUILT_IN_USER_ID))) {
            metricByName.put(e.getName(), e.getMetricType());
        }

        List<ProgramTemplate> templates = templateMapper.selectList(null);
        List<String> problems = new ArrayList<>();
        int checked = 0;

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
                        continue;
                    }
                    var metric = metricByName.get(nameNode.asText());
                    if (metric != com.gymlog.exercise.MetricType.DURATION
                            && metric != com.gymlog.exercise.MetricType.DISTANCE_DURATION) {
                        continue;
                    }
                    checked++;
                    String where = t.getCode() + " / " + nameNode.asText();

                    JsonNode dur = ex.get("targetDurationSec");
                    if (dur == null || dur.isNull() || dur.asInt() <= 0) {
                        problems.add(where + " : 缺少 targetDurationSec（跟练页不会有倒计时）");
                    }

                    // reps 字段必须清干净。留着的话客户端 repsLabel 会显示
                    // 「30-60」，语音播报会念「30 次」——那是秒数，不是次数。
                    if (ex.hasNonNull("targetRepsMin") || ex.hasNonNull("targetRepsMax")) {
                        problems.add(where + " : reps 字段没清掉（V14 的迁移没生效？）");
                    }
                }
            }
        }

        assertThat(checked)
                .as("模板里应当有时长类动作，否则这条测试是空转的")
                .isGreaterThan(0);

        assertThat(problems)
                .as("时长类动作的模板字段不对：\n" + String.join("\n", problems))
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
