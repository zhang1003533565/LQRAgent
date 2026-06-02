package com.lqragent.backend.agents.contentanalyzer.service;

import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgePointRepository;
import com.lqragent.backend.core.llm.LlmContentGenerator;
import com.lqragent.backend.storage.QiniuStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 内容分析智能体。
 * 读取上传文档内容，通过 RAG 检索 + 关键词匹配提取相关知识点。
 * PDF 文件使用 PDFBox 提取文本，语义检索优先使用 ai-server RAG。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAnalyzerService {

    private final KnowledgePointRepository knowledgePointRepo;
    private final LlmContentGenerator llmGenerator;
    private final QiniuStorageService qiniuStorageService;
    private final AiServerClient aiServerClient;

    /**
     * 对上传文件执行内容分析。
     *
     * @param filePath 文件路径
     * @param fileName 文件名（用于日志）
     * @return 分析结果 JSON：包含 summary + mappedKpIds
     */
    public AnalysisResult analyze(String filePath, String fileName) {
        log.info("[ContentAnalyzer] analyzing: file={}", fileName);

        String content = readFileContent(filePath, fileName);
        if (content == null || content.isBlank()) {
            return new AnalysisResult("无法读取文件内容", List.of());
        }

        // 内容摘要（截取前 200 字）
        String summary = content.length() > 200
                ? content.substring(0, 200) + "..."
                : content;

        // 优先 LLM 分析，失败降级子串匹配
        List<String> matchedKpIds = analyzeWithLlm(content);
        if (matchedKpIds.isEmpty()) {
            matchedKpIds = matchKnowledgePoints(content);
        }

        log.info("[ContentAnalyzer] done: file={}, matched={}", fileName, matchedKpIds);
        return new AnalysisResult(summary, matchedKpIds);
    }

    /** 从七牛云下载文件并读取文本内容，PDF 使用 PDFBox 提取 */
    private String readFileContent(String objectKey, String fileName) {
        try {
            byte[] bytes = qiniuStorageService.download(objectKey);
            if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
                // PDF 用 PDFBox 提取文本
                try (PDDocument doc = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(doc);
                }
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[ContentAnalyzer] read error: key={}, error={}", objectKey, e.getMessage());
            return null;
        }
    }

    /**
     * 知识点匹配：优先 RAG 语义检索，降级子串匹配。
     * RAG 检索将文档内容作为 query 在公共知识库中搜索相关片段，
     * 再从检索结果中匹配知识点标题。
     */
    private List<String> matchKnowledgePoints(String content) {
        // 尝试 RAG 语义检索
        try {
            String truncated = content.length() > 2000 ? content.substring(0, 2000) : content;
            List<Map<String, Object>> results = aiServerClient.searchKnowledgeBase(
                    "kb-public", truncated, 5
            );

            if (results != null && !results.isEmpty()) {
                // 从检索结果中提取文本，再映射到知识点
                String retrievedText = results.stream()
                        .map(r -> String.valueOf(r.getOrDefault("text", "")))
                        .collect(Collectors.joining("\n"));
                List<String> ragMatched = mapTextToKnowledgePoints(retrievedText);
                if (!ragMatched.isEmpty()) {
                    log.info("[ContentAnalyzer] RAG matched {} KPs", ragMatched.size());
                    return ragMatched;
                }
            }
        } catch (Exception e) {
            log.warn("[ContentAnalyzer] RAG search failed, fallback to substring: {}", e.getMessage());
        }

        // 降级：子串匹配
        return matchBySubstring(content);
    }

    /** 从文本中匹配知识点标题（规则匹配） */
    private List<String> mapTextToKnowledgePoints(String text) {
        List<KnowledgePoint> allKps = knowledgePointRepo.findAll();
        String lower = text.toLowerCase();
        return allKps.stream()
                .filter(kp -> lower.contains(kp.getTitle().toLowerCase()))
                .map(KnowledgePoint::getKpId)
                .collect(Collectors.toList());
    }

    /** 原始子串匹配（降级方案） */
    private List<String> matchBySubstring(String content) {
        List<KnowledgePoint> allKps = knowledgePointRepo.findAll();
        String lowerContent = content.toLowerCase();
        List<String> matched = new ArrayList<>();

        for (KnowledgePoint kp : allKps) {
            String title = kp.getTitle().toLowerCase();
            String desc = kp.getDescription() != null ? kp.getDescription().toLowerCase() : "";
            if (lowerContent.contains(title) || (desc.length() > 5 && lowerContent.contains(desc))) {
                matched.add(kp.getKpId());
            }
        }
        log.info("[ContentAnalyzer] substring matched {} KPs (fallback)", matched.size());
        return matched;
    }

    /** LLM 分析文档覆盖的知识点，失败返回空列表 */
    private List<String> analyzeWithLlm(String content) {
        try {
            List<KnowledgePoint> allKps = knowledgePointRepo.findAll();
            String kpList = allKps.stream()
                    .map(kp -> kp.getKpId() + ": " + kp.getTitle())
                    .collect(Collectors.joining("\n"));
            String truncated = content.length() > 3000
                    ? content.substring(0, 3000) : content;

            String result = llmGenerator.generate("content_analysis",
                    "知识点匹配",
                    "以下是课程知识点列表：\n" + kpList
                    + "\n\n以下是上传文档内容：\n" + truncated
                    + "\n\n请返回文档覆盖的知识点ID列表（JSON数组格式，如 [\"kp_decorator\",\"kp_function\"]）");

            if (result != null && result.contains("[")) {
                String arrayStr = result.substring(result.indexOf("["), result.lastIndexOf("]") + 1);
                return Arrays.stream(arrayStr.replaceAll("[\"\\s]", "").split(","))
                        .filter(s -> !s.isBlank())
                        .toList();
            }
        } catch (Exception e) {
            log.warn("[ContentAnalyzer] LLM 分析失败，降级子串匹配: {}", e.getMessage());
        }
        return List.of();
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
