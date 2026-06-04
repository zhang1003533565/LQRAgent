package com.lqragent.backend.agents.serve.qa.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "search_knowledge"; }
    
    @Override
    public String description() { return "搜索知识库获取相关信息"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "搜索关键词"),
                        "kbName", Map.of("type", "string", "description", "知识库名称")
                ),
                "required", new String[]{"query"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String query = args.get("query").toString();
            // TODO: 调用 RAG 搜索
            return ToolResult.success("{\"results\": []}");
        } catch (Exception e) {
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }
}
