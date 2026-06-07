package com.lqragent.backend.agents.base;

import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 共享的 RAG 搜索工具
 * 可以被任何 Agent 使用，从知识库中检索相关信息
 */
@Component
@RequiredArgsConstructor
public class RagSearchTool implements AgentTool {
    
    private final AiServerWsProxy aiServerWsProxy;
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
            
            // 调用 ai-server 的 RAG 检索
            String result = aiServerWsProxy.callCapability(
                "rag_search",
                Map.of("query", query, "top_k", topK)
            );
            
            if (result != null && !result.isBlank()) {
                return ToolResult.success(result);
            }
            
            // 降级：返回提示
            try {
                return ToolResult.success(mapper.writeValueAsString(Map.of(
                        "query", query,
                        "results", List.of(),
                        "summary", "知识库检索未返回结果，请基于通用知识回答"
                )));
            } catch (Exception ex) {
                return ToolResult.success("{\"query\":\"" + query + "\",\"results\":[],\"summary\":\"知识库检索未返回结果\"}");
            }
        } catch (Exception e) {
            try {
                return ToolResult.success(mapper.writeValueAsString(Map.of(
                        "query", args.get("query"),
                        "results", List.of(),
                        "summary", "知识库检索失败，请基于通用知识回答: " + e.getMessage()
                )));
            } catch (Exception ex) {
                return ToolResult.success("{\"results\":[],\"summary\":\"知识库检索失败\"}");
            }
        }
    }
}
