package com.lqragent.backend.agents.serve.qa.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AgentTool {
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "search_knowledge"; }
    
    @Override
    public String description() { return "从知识库中检索相关信息，用于回答用户问题"; }
    
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
            return ToolResult.success(mapper.writeValueAsString(Map.of(
                    "query", query,
                    "results", java.util.List.of(),
                    "totalFound", 0,
                    "summary", "知识库未启用（本地模式），请基于自身知识回答"
            )));
        } catch (Exception e) {
            return ToolResult.failure("知识检索失败: " + e.getMessage());
        }
    }
}
