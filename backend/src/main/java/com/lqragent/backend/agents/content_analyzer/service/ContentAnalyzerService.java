package com.lqragent.backend.agents.content_analyzer.service;

import com.lqragent.backend.agents.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.agents.knowledgegraph.repository.KnowledgePointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 内容分析智能体。
 * 读取上传文档内容，通过关键词匹配提取相关知识点。
 * P3 实现简单文本匹配，后续可升级为 LLM 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAnalyzerService {

    private final KnowledgePointRepository knowledgePointRepo;

    /**
     * 对上传文件执行内容分析。
     *
     * @param filePath 文件路径
     * @param fileName 文件名（用于日志）
     * @return 分析结果 JSON：包含 summary + mappedKpIds
     */
    public AnalysisResult analyze(String filePath, String fileName) {
        log.info("[ContentAnalyzer] analyzing: file={}", fileName);

        String content = readFileContent(filePath);
        if (content == null || content.isBlank()) {
            return new AnalysisResult("无法读取文件内容", List.of());
        }

        // 内容摘要（截取前 200 字）
        String summary = content.length() > 200
                ? content.substring(0, 200) + "..."
                : content;

        // 关键词匹配知识点
        List<String> matchedKpIds = matchKnowledgePoints(content);

        log.info("[ContentAnalyzer] done: file={}, matched={}", fileName, matchedKpIds);
        return new AnalysisResult(summary, matchedKpIds);
    }

    /** 读取文本文件内容 */
    private String readFileContent(String filePath) {
        try {
            Path path = Path.of(filePath);
            if (!Files.exists(path) || !Files.isReadable(path)) {
                log.warn("[ContentAnalyzer] file not found or not readable: {}", filePath);
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[ContentAnalyzer] read error: {}: {}", filePath, e.getMessage());
            return null;
        }
    }

    /** 关键词匹配：扫描知识点标题/描述中的词是否出现在文档里 */
    private List<String> matchKnowledgePoints(String content) {
        List<KnowledgePoint> allKps = knowledgePointRepo.findAll();
        String lowerContent = content.toLowerCase();
        List<String> matched = new ArrayList<>();

        for (KnowledgePoint kp : allKps) {
            // 检查标题或描述是否在内容中出现
            String title = kp.getTitle().toLowerCase();
            String desc = kp.getDescription() != null ? kp.getDescription().toLowerCase() : "";
            if (lowerContent.contains(title) || (desc.length() > 5 && lowerContent.contains(desc))) {
                matched.add(kp.getKpId());
            }
        }
        return matched;
    }

    /** 分析结果 */
    public record AnalysisResult(String summary, List<String> mappedKpIds) {
        public String toJson() {
            return "{\"summary\":\"" + escape(summary) + "\",\"mappedKpIds\":" +
                    mappedKpIds.stream().collect(Collectors.joining("\",\"", "[\"", "\"]")) +
                    "}";
        }

        private static String escape(String s) {
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
        }
    }
}
