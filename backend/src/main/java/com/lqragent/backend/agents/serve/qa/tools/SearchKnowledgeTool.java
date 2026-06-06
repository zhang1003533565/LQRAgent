package com.lqragent.backend.agents.serve.qa.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchKnowledgeTool implements AgentTool {
    
    private final AiServerWsProxy aiServerWsProxy;
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
            int topK = args.containsKey("topK") ? Integer.parseInt(args.get("topK").toString()) : 3;
            
            // 调用 ai-server 知识库检索
            // 注意：这里简化实现，实际应该调用 ai-server 的检索接口
            // 暂时返回模拟结果
            
            java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
            
            // 模拟检索结果
            results.add(Map.of(
                    "content", "Python 是一种解释型、面向对象、动态数据类型的高级程序设计语言。",
                    "source", "python_basics.md",
                    "score", 0.95
            ));
            
            results.add(Map.of(
                    "content", "Python 由 Guido van Rossum 于 1991 年底设计第一个公开发行版。",
                    "source", "python_history.md",
                    "score", 0.85
            ));
            
            results.add(Map.of(
                    "content", "Python 支持多种编程范式，包括面向对象、命令式、函数式和过程式编程。",
                    "source", "python_features.md",
                    "score", 0.80
            ));
            
            // 限制返回数量
            if (results.size() > topK) {
                results = results.subList(0, topK);
            }
            
            return ToolResult.success(mapper.writeValueAsString(Map.of(
                    "query", query,
                    "results", results,
                    "totalFound", results.size(),
                    "summary", "找到 " + results.size() + " 条相关知识"
            )));
        } catch (Exception e) {
            return ToolResult.failure("知识检索失败: " + e.getMessage());
        }
    }
}
