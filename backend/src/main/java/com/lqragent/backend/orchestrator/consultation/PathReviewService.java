package com.lqragent.backend.orchestrator.consultation;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 路径难度评审（Phase 2）：Difficulty Agent 的轻量实现，输出 approve / revise。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PathReviewService {

    private final LlmClient llmClient;
    private final AppRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PathReviewDecision review(String profileSummary, LearningPathDto path, String goal) {
        if (path == null || path.getNodes() == null || path.getNodes().isEmpty()) {
            return PathReviewDecision.approve("路径节点为空，跳过高阶评审");
        }

        String nodeSummary = path.getNodes().stream()
                .limit(8)
                .map(n -> n.getTitle() != null ? n.getTitle() : n.getKpId())
                .reduce((a, b) -> a + " → " + b)
                .orElse("");

        try {
            String system = """
                    你是学习路径难度评审专家。根据学习者画像与路径草案，判断难度是否匹配。
                    只返回 JSON：{"decision":"approve"|"revise","summary":"一句话说明","feedback":"若 revise 则给出调整建议"}
                    """;
            String user = "学习目标：" + goal + "\n"
                    + "学习者画像：" + (profileSummary != null ? profileSummary : "未知") + "\n"
                    + "路径节点：" + nodeSummary + "\n"
                    + "节点数：" + path.getNodes().size();

            String raw = llmClient.chat(system, java.util.List.of(
                    java.util.Map.of("role", "user", "content", user)), null).content();

            JsonNode root = objectMapper.readTree(extractJson(raw));
            String decision = root.path("decision").asText("approve");
            String summary = root.path("summary").asText("评审完成");
            String feedback = root.path("feedback").asText("");
            if ("revise".equalsIgnoreCase(decision) && !feedback.isBlank()) {
                return PathReviewDecision.revise(summary, feedback);
            }
            return PathReviewDecision.approve(summary);
        } catch (Exception e) {
            log.warn("[PathReview] LLM review failed, default approve: {}", e.getMessage());
            return heuristicReview(profileSummary, path, nodeSummary);
        }
    }

    private PathReviewDecision heuristicReview(String profileSummary, LearningPathDto path, String nodeSummary) {
        String profile = profileSummary != null ? profileSummary.toLowerCase() : "";
        boolean beginner = profile.contains("零基础") || profile.contains("初学") || profile.contains("入门");
        boolean advancedGoal = nodeSummary.toLowerCase().contains("项目实战")
                || nodeSummary.toLowerCase().contains("高级")
                || path.getNodes().size() > 10;
        if (beginner && advancedGoal) {
            return PathReviewDecision.revise(
                    "路径对初学者偏难",
                    "请减少节点数量，前面增加基础语法与环境搭建，项目实战后置");
        }
        return PathReviewDecision.approve("启发式评审通过");
    }

    private static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }
}
