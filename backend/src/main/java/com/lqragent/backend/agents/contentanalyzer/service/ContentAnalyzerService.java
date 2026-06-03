package com.lqragent.backend.agents.contentanalyzer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

                知识点列表：
                %s

                输出必须是严格 JSON，格式如下：
                {"summary":"...","mappedKpIds":["kp_xxx","kp_yyy"]}
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
            return new AnalysisResult(summary, List.copyOf(mapped));
        } catch (Exception e) {
            log.warn("[ContentAnalyzer] failed to parse ai-server response: {}",
                    response == null ? "<null>" : response);
            throw new IllegalArgumentException("Failed to parse ai-server analysis result: " + e.getMessage(), e);
        }
    }

    private String extractJsonObject(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("AI response does not contain JSON: " + response);
        }
        return response.substring(start, end + 1);
    }

    public record AnalysisResult(String summary, List<String> mappedKpIds) {
        public String toJson() {
            return "{\"summary\":\"" + escape(summary) + "\",\"mappedKpIds\":"
                    + mappedKpIds.stream().collect(Collectors.joining("\",\"", "[\"", "\"]"))
                    + "}";
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
        }
    }
}
