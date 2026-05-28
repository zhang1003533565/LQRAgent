package com.lqragent.backend.agents.learner_profile.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.learner_profile.entity.LearnerProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 LLM / 规则抽取结果增量合并进画像库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileMergeService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern LEARNED = Pattern.compile("学过\\s*([^，,。但\\s]+)");
    private static final Pattern WEAK = Pattern.compile("(?:不懂|不会|没学过)\\s*([^，,。\\s]+)");

    /**
     * 将 LLM 返回的 JSON 合并进画像（增量更新，不覆盖未提及字段）。
     */
    public void mergeFromLlmJson(LearnerProfile profile, String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return;
        try {
            String json = unwrapMarkdownJson(rawJson);
            JsonNode node = JSON.readTree(json);

            if (node.hasNonNull("knowledge_base")) {
                profile.setKnowledgeLevel(mapKnowledgeLevel(node.get("knowledge_base").asText()));
            }
            if (node.hasNonNull("learning_goal")) {
                profile.setLearningGoal(node.get("learning_goal").asText());
            }
            if (node.hasNonNull("cognitive_style")) {
                profile.setCognitiveStyle(mapCognitiveStyle(node.get("cognitive_style").asText()));
            }
            if (node.has("weakness") && node.get("weakness").isArray()) {
                mergeWeakness(profile, node.get("weakness"));
            }
            if (node.hasNonNull("learning_pace")) {
                profile.setLearningPace(mapLearningPace(node.get("learning_pace").asText()));
            }
            if (node.hasNonNull("interest")) {
                profile.setInterestDirection(toJsonArray(node.get("interest").asText()));
            }
            if (node.has("mastered_topics") && node.get("mastered_topics").isArray()) {
                Map<String, String> mastery = readTopicMastery(profile.getTopicMastery());
                node.get("mastered_topics").forEach(t -> mastery.put(t.asText().trim(), "MASTERED"));
                profile.setTopicMastery(JSON.writeValueAsString(mastery));
            }
            if (node.has("pending_topics") && node.get("pending_topics").isArray()) {
                Map<String, String> mastery = readTopicMastery(profile.getTopicMastery());
                node.get("pending_topics").forEach(t -> mastery.put(t.asText().trim(), "PENDING"));
                profile.setTopicMastery(JSON.writeValueAsString(mastery));
            }
        } catch (Exception e) {
            log.warn("[ProfileMerge] LLM JSON 解析失败，尝试规则抽取: {}", e.getMessage());
        }
    }

    /**
     * 规则兜底：从单句对话识别「学过 X / 不懂 Y」并增量写入 topicMastery。
     */
    public void mergeFromDialogueRules(LearnerProfile profile, String text) {
        if (text == null || text.isBlank()) return;
        Map<String, String> mastery = readTopicMastery(profile.getTopicMastery());
        boolean changed = false;

        Matcher learned = LEARNED.matcher(text);
        while (learned.find()) {
            String topic = learned.group(1).trim();
            if (!topic.isEmpty()) {
                mastery.put(topic, "MASTERED");
                changed = true;
            }
        }
        Matcher weak = WEAK.matcher(text);
        while (weak.find()) {
            String topic = weak.group(1).trim();
            if (!topic.isEmpty()) {
                mastery.put(topic, "PENDING");
                changed = true;
            }
        }
        if (changed) {
            try {
                profile.setTopicMastery(JSON.writeValueAsString(mastery));
            } catch (Exception e) {
                log.warn("[ProfileMerge] topicMastery 序列化失败", e);
            }
        }
    }

    public Map<String, String> readTopicMastery(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return new LinkedHashMap<>(JSON.readValue(json, new TypeReference<Map<String, String>>() {}));
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private void mergeWeakness(LearnerProfile profile, JsonNode weaknessNode) {
        StringBuilder sb = new StringBuilder();
        weaknessNode.forEach(n -> {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(n.asText());
        });
        String patch = "[\"" + sb.toString().replace("\"", "\\\"") + "\"]";
        String existing = profile.getCommonErrors();
        if (existing == null || existing.isBlank() || "[]".equals(existing.trim())) {
            profile.setCommonErrors(patch);
        } else if (!existing.contains(sb.toString())) {
            profile.setCommonErrors(existing.replace("]", ", \"" + sb + "\"]"));
        }
    }

    private String unwrapMarkdownJson(String raw) {
        String s = raw.trim();
        if (s.startsWith("```")) {
            int start = s.indexOf('\n');
            int end = s.lastIndexOf("```");
            if (start >= 0 && end > start) {
                s = s.substring(start + 1, end).trim();
            }
        }
        int brace = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (brace >= 0 && last > brace) {
            s = s.substring(brace, last + 1);
        }
        return s;
    }

    private String mapKnowledgeLevel(String text) {
        if (text == null) return "BEGINNER";
        if (text.contains("熟练") || text.contains("进阶")) return "ADVANCED";
        if (text.contains("基础") || text.contains("中等")) return "INTERMEDIATE";
        return "BEGINNER";
    }

    private String mapCognitiveStyle(String text) {
        if (text == null) return "reading";
        if (text.contains("视觉")) return "visual";
        if (text.contains("动手") || text.contains("实践")) return "practice";
        if (text.contains("听觉")) return "auditory";
        return "reading";
    }

    private String mapLearningPace(String text) {
        if (text == null) return "NORMAL";
        if (text.contains("快")) return "FAST";
        if (text.contains("慢")) return "SLOW";
        return "NORMAL";
    }

    private String toJsonArray(String interest) {
        if (interest == null || interest.isBlank()) return "[]";
        if (interest.trim().startsWith("[")) return interest.trim();
        return "[\"" + interest.replace("\"", "\\\"") + "\"]";
    }
}
