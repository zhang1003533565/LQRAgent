package com.lqragent.backend.agents.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 共享的 RAG 搜索工具
 * 检索路径：七牛云原始文件 → ai-server 解析 → Docker Chroma 向量索引
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagSearchTool implements AgentTool {

    private final AppRuntimeConfig runtimeConfig;
    private final AiServerClient aiServerClient;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() { return "search_knowledge"; }

    @Override
    public String description() { return "从知识库中检索相关信息，用于增强回答的准确性"; }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索查询"),
                        "topK", Map.of("type", "integer", "description", "返回结果数量"),
                        "userId", Map.of("type", "string", "description", "用户ID（用于检索私有知识库）")
                ),
                "required", new String[]{"query"}
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String query = args.get("query").toString();
            int topK = args.containsKey("topK") ? Integer.parseInt(args.get("topK").toString()) : 3;

            List<String> kbNames = resolveKbNames(args);
            if (kbNames.isEmpty()) {
                return emptyResult(query);
            }

            List<Map<String, Object>> allSources = new ArrayList<>();
            StringBuilder contextBuilder = new StringBuilder();
            boolean anySuccess = false;

            for (String kbName : kbNames) {
                Map<String, Object> result = aiServerClient.searchKnowledgeBase(kbName, query, topK);
                if (result == null || result.isEmpty()) {
                    continue;
                }
                if (Boolean.TRUE.equals(result.get("needs_reindex"))) {
                    log.warn("[RagSearchTool] KB '{}' needs reindex (Chroma), skipping", kbName);
                    continue;
                }
                anySuccess = true;
                mergeSearchResult(kbName, result, allSources, contextBuilder);
            }

            if (!anySuccess || allSources.isEmpty()) {
                return emptyResult(query);
            }

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("ragSources", allSources);
            metadata.put("knowledgeBases", kbNames);

            String payload = mapper.writeValueAsString(Map.of(
                    "query", query,
                    "sources", allSources,
                    "summary", contextBuilder.length() > 0 ? contextBuilder.toString() : "检索到 " + allSources.size() + " 条参考"
            ));
            return ToolResult.success(payload, metadata);
        } catch (Exception e) {
            log.warn("[RagSearchTool] search failed: {}", e.getMessage());
            return emptyResult(String.valueOf(args.get("query")));
        }
    }

    private ToolResult emptyResult(String query) {
        try {
            return ToolResult.success(mapper.writeValueAsString(Map.of(
                    "query", query,
                    "results", List.of(),
                    "summary", "知识库暂无相关内容"
            )));
        } catch (Exception e) {
            return ToolResult.success("{\"results\":[],\"summary\":\"知识库检索失败\"}");
        }
    }

    /**
     * 解析待检索知识库：公共库 + 用户私有库（均存在时才检索）
     */
    private List<String> resolveKbNames(Map<String, Object> args) {
        List<String> names = new ArrayList<>();
        String publicKb = runtimeConfig.get(ConfigKeys.KB_PUBLIC, "kb-public");
        if (aiServerClient.knowledgeBaseExists(publicKb)) {
            names.add(publicKb);
        }

        Object userId = args.get("userId");
        if (userId != null) {
            String privateKb = runtimeConfig.get(ConfigKeys.KB_PRIVATE_PREFIX, "kb-private-") + userId;
            if (aiServerClient.knowledgeBaseExists(privateKb) && !names.contains(privateKb)) {
                names.add(privateKb);
            }
        }

        if (names.isEmpty()) {
            log.warn("[RagSearchTool] no registered KB found (public={}, userId={})", publicKb, userId);
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private void mergeSearchResult(String kbName, Map<String, Object> result,
                                   List<Map<String, Object>> allSources, StringBuilder contextBuilder) {
        Object sourcesObj = result.get("sources");
        if (sourcesObj instanceof List<?> list) {
            Set<String> seen = new LinkedHashSet<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> source = new LinkedHashMap<>((Map<String, Object>) map);
                    source.putIfAbsent("kbName", kbName);
                    String key = String.valueOf(source.getOrDefault("chunk_id",
                            source.getOrDefault("content", "")));
                    if (seen.add(key)) {
                        allSources.add(source);
                    }
                }
            }
        }

        String content = firstNonBlank(
                asText(result.get("answer")),
                asText(result.get("content"))
        );
        if (!content.isBlank()) {
            if (contextBuilder.length() > 0) {
                contextBuilder.append("\n\n");
            }
            contextBuilder.append("[").append(kbName).append("]\n").append(content);
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
