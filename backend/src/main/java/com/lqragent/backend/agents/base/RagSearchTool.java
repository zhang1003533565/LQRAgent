package com.lqragent.backend.agents.base;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * 共享的 RAG 搜索工具
 * 可以被任何 Agent 使用，从知识库中检索相关信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagSearchTool implements AgentTool {
    
    private final AppRuntimeConfig runtimeConfig;
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    
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
                        "topK", Map.of("type", "integer", "description", "返回结果数量")
                ),
                "required", new String[]{"query"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String query = args.get("query").toString();
            int topK = args.containsKey("topK") ? Integer.parseInt(args.get("topK").toString()) : 3;
            
            // 尝试通过 ai-server REST API 搜索知识库
            String result = searchKnowledgeBase(query, topK);
            
            if (result != null && !result.isBlank()) {
                return ToolResult.success(result);
            }
            
            // 降级：使用 LLM 通用知识
            return fallbackToLlm(query);
        } catch (Exception e) {
            log.warn("[RagSearchTool] search failed: {}", e.getMessage());
            try {
                return ToolResult.success(mapper.writeValueAsString(Map.of(
                        "query", args.get("query"),
                        "results", List.of(),
                        "summary", "知识库检索失败，请基于通用知识回答"
                )));
            } catch (Exception ex) {
                return ToolResult.success("{\"results\":[],\"summary\":\"知识库检索失败\"}");
            }
        }
    }
    
    /**
     * 通过 ai-server REST API 搜索知识库
     */
    private String searchKnowledgeBase(String query, int topK) {
        try {
            String baseUrl = runtimeConfig.getAiServerBaseUrl();
            String searchUrl = baseUrl + "/api/v1/knowledge/kb-public/search";
            
            String requestBody = "query=" + java.net.URLEncoder.encode(query, "UTF-8") + "&top_k=" + topK;
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                log.info("[RagSearchTool] knowledge base search success: query={}", query);
                return response.body();
            } else {
                log.warn("[RagSearchTool] knowledge base search failed: status={}", response.statusCode());
                return null;
            }
        } catch (Exception e) {
            log.warn("[RagSearchTool] knowledge base search exception: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 降级：使用 LLM 通用知识
     */
    private ToolResult fallbackToLlm(String query) {
        try {
            String prompt = String.format(
                "请基于你的知识回答以下问题：\n\n问题：%s", query);
            
            var response = llmClient.chat(
                "你是一个知识检索助手，请根据用户的问题提供相关信息。",
                List.of(Map.of("role", "user", "content", prompt)),
                null
            );
            
            if (response.isSuccess() && response.content() != null) {
                return ToolResult.success(response.content());
            }
            
            return ToolResult.success(mapper.writeValueAsString(Map.of(
                    "query", query,
                    "results", List.of(),
                    "summary", "知识库检索未返回结果，请基于通用知识回答"
            )));
        } catch (Exception e) {
            return ToolResult.success("{\"results\":[],\"summary\":\"知识库检索失败\"}");
        }
    }
}
