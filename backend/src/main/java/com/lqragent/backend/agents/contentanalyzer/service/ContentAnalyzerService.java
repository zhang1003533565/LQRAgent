package com.lqragent.backend.agents.contentanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAnalyzerService {

    private final KnowledgePointRepository knowledgePointRepo;
    private final AiServerWsProxy aiServerWsProxy;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisResult analyze(String kbName, String fileName) {
        log.info("[ContentAnalyzer] analyzing via ai-server: kb={}, file={}", kbName, fileName);

        List<KnowledgePoint> allKps = knowledgePointRepo.findAll().stream()
                .sorted(Comparator.comparing(KnowledgePoint::getKpId))
                .toList();

        String response = executeAnalysis(kbName, buildPrompt(fileName, allKps));
        AnalysisResult result = parseAnalysisResult(response, allKps);
        log.info("[ContentAnalyzer] done: file={}, matched={}", fileName, result.mappedKpIds());
        return result;
    }

    private String buildPrompt(String fileName, List<KnowledgePoint> allKps) {
        String kpList = allKps.stream()
                .map(kp -> kp.getKpId() + ": " + kp.getTitle()
                        + (kp.getDescription() != null && !kp.getDescription().isBlank() ? " - " + kp.getDescription() : ""))
                .collect(Collectors.joining("\n"));
        return """
                你是一个课程内容分析助手。请基于当前知识库中与文件“%s”相关的检索内容，完成两件事：
                1. 给出 120 字以内的中文内容摘要。
                2. 从给定知识点列表中挑选最相关的知识点 ID，最多 8 个。
                3. 为每个选中的知识点给出 0-100 的匹配度分数，数值越高表示相关性越强。

                知识点列表：
                %s

                输出必须是严格 JSON，格式如下：
                {"summary":"...","mappedKpIds":["kp_xxx","kp_yyy"],"matchedKnowledgePoints":[{"kpId":"kp_xxx","score":92},{"kpId":"kp_yyy","score":81}]}
                要求：
                - `matchedKnowledgePoints` 与 `mappedKpIds` 保持一致
                - `score` 必须是 0-100 的整数
                除 JSON 外不要输出任何额外文字。
                """.formatted(fileName, kpList);
    }

    private String executeAnalysis(String kbName, String prompt) {
        StringBuilder response = new StringBuilder();
        StringBuilder error = new StringBuilder();

        aiServerWsProxy.streamChat(null, prompt, List.of(kbName), new AiServerWsProxy.StreamCallback() {
            @Override
            public void onChunk(String content) {
                response.append(content);
            }

            @Override
            public void onDone(String aiServerSessionId) {
                // no-op
            }

            @Override
            public void onError(String errorMessage) {
                if (error.length() == 0) {
                    error.append(errorMessage);
                }
            }
        });

        if (error.length() > 0) {
            throw new IllegalStateException(error.toString());
        }
        return response.toString();
    }

    private AnalysisResult parseAnalysisResult(String response, List<KnowledgePoint> allKps) {
        try {
            JsonNode root = objectMapper.readTree(extractJsonObject(response));
            String summary = root.path("summary").asText("").trim();
            if (summary.isBlank()) {
                summary = "未生成摘要";
            }

            Set<String> validKpIds = allKps.stream().map(KnowledgePoint::getKpId).collect(Collectors.toSet());
            Set<String> mapped = new LinkedHashSet<>();
            JsonNode mappedNode = root.path("mappedKpIds");
            if (mappedNode.isArray()) {
                for (JsonNode node : mappedNode) {
                    String kpId = node.asText("").trim();
                    if (!kpId.isBlank() && validKpIds.contains(kpId)) {
                        mapped.add(kpId);
                    }
                }
            }

            List<MatchedKnowledgePoint> matchedKnowledgePoints = parseMatchedKnowledgePoints(
                    root.path("matchedKnowledgePoints"), validKpIds);
            if (matchedKnowledgePoints.isEmpty()) {
                matchedKnowledgePoints = mapped.stream()
                        .map(kpId -> new MatchedKnowledgePoint(kpId, null))
                        .toList();
            } else {
                matchedKnowledgePoints.forEach(item -> mapped.add(item.kpId()));
            }

            return new AnalysisResult(summary, List.copyOf(mapped), matchedKnowledgePoints);
        } catch (Exception e) {
            log.warn("[ContentAnalyzer] failed to parse ai-server response: {}",
                    response == null ? "<null>" : response);
            throw new IllegalArgumentException("Failed to parse ai-server analysis result: " + e.getMessage(), e);
        }
    }

    private List<MatchedKnowledgePoint> parseMatchedKnowledgePoints(JsonNode matchedNode, Set<String> validKpIds) {
        Set<String> seen = new LinkedHashSet<>();
        List<MatchedKnowledgePoint> matched = new ArrayList<>();
        if (matchedNode.isArray()) {
            for (JsonNode node : matchedNode) {
                MatchedKnowledgePoint point = parseMatchedKnowledgePointNode(node, validKpIds);
                if (point != null && seen.add(point.kpId())) {
                    matched.add(point);
                }
            }
            return matched;
        }

        if (matchedNode.isObject()) {
            matchedNode.fields().forEachRemaining(entry -> {
                String kpId = entry.getKey() == null ? "" : entry.getKey().trim();
                if (kpId.isBlank() || !validKpIds.contains(kpId) || !seen.add(kpId)) {
                    return;
                }
                matched.add(new MatchedKnowledgePoint(kpId, parseScoreNode(entry.getValue())));
            });
        }

        return matched;
    }

    private MatchedKnowledgePoint parseMatchedKnowledgePointNode(JsonNode node, Set<String> validKpIds) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            String kpId = node.asText("").trim();
            if (kpId.isBlank() || !validKpIds.contains(kpId)) {
                return null;
            }
            return new MatchedKnowledgePoint(kpId, null);
        }

        if (!node.isObject()) {
            return null;
        }

        String kpId = firstNonBlank(
                node.path("kpId").asText("").trim(),
                node.path("id").asText("").trim(),
                node.path("knowledgePointId").asText("").trim()
        );
        if (kpId.isBlank() || !validKpIds.contains(kpId)) {
            return null;
        }

        Integer score = parseScoreNode(node.path("score"));
        if (score == null) score = parseScoreNode(node.path("matchScore"));
        if (score == null) score = parseScoreNode(node.path("matchingScore"));
        if (score == null) score = parseScoreNode(node.path("similarity"));
        if (score == null) score = parseScoreNode(node.path("relevance"));
        if (score == null) score = parseScoreNode(node.path("confidence"));
        return new MatchedKnowledgePoint(kpId, score);
    }

    private Integer parseScoreNode(JsonNode scoreNode) {
        if (scoreNode == null || scoreNode.isMissingNode() || scoreNode.isNull()) {
            return null;
        }
        if (scoreNode.isNumber()) {
            double raw = scoreNode.doubleValue();
            return clampScore((int) Math.round(raw <= 1 ? raw * 100 : raw));
        }
        if (scoreNode.isTextual()) {
            String text = scoreNode.asText("").trim();
            if (text.isBlank()) {
                return null;
            }
            try {
                double raw = Double.parseDouble(text);
                return clampScore((int) Math.round(raw <= 1 ? raw * 100 : raw));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String extractJsonObject(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("AI response does not contain JSON: " + response);
        }
        return response.substring(start, end + 1);
    }

    public record AnalysisResult(
            String summary,
            List<String> mappedKpIds,
            List<MatchedKnowledgePoint> matchedKnowledgePoints
    ) {
        public String toJson() {
            String mappedJson = mappedKpIds.stream()
                    .map(kpId -> "\"" + escape(kpId) + "\"")
                    .collect(Collectors.joining(",", "[", "]"));
            String matchedJson = matchedKnowledgePoints.stream()
                    .map(item -> "{\"kpId\":\"" + escape(item.kpId()) + "\",\"score\":"
                            + (item.score() == null ? "null" : String.format(Locale.ROOT, "%d", item.score()))
                            + "}")
                    .collect(Collectors.joining(",", "[", "]"));
            return "{\"summary\":\"" + escape(summary) + "\",\"mappedKpIds\":" + mappedJson
                    + ",\"matchedKnowledgePoints\":" + matchedJson
                    + "}";
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
    }

    public record MatchedKnowledgePoint(String kpId, Integer score) {
    }
}
