package com.lqragent.backend.agents.summary.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.base.LlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateSummaryTool implements AgentTool {
    
    private final LlmClient llmClient;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_summary"; }
    
    @Override
    public String description() { return "调用 LLM 生成学习总结和复习材料"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "要总结的主题"),
                        "content", Map.of("type", "string", "description", "原始内容（可选）")
                ),
                "required", new String[]{"topic"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String topic = args.get("topic") != null ? args.get("topic").toString() : "学习内容";
            String content = args.get("content") != null ? args.get("content").toString() : "";
            
            // 调用 LLM 生成总结
            String prompt = content.isEmpty()
                    ? String.format("请为「%s」生成一份学习总结，包含：核心要点、重点难点、复习建议。使用Markdown格式。", topic)
                    : String.format("请为以下内容生成学习总结：\n\n%s\n\n使用Markdown格式，包含核心要点和复习建议。", content);
            
            String summary = llmClient.chatSimple(
                "你是学习总结专家。生成结构清晰、重点突出的学习总结。使用Markdown格式。",
                prompt
            );
            
            if (summary == null || summary.isBlank()) {
                summary = "# " + topic + " 学习总结\n\n## 核心要点\n1. 理解基本概念\n2. 掌握核心语法\n3. 实践应用\n\n## 复习建议\n- 每天花30分钟复习\n- 多做练习题";
            }
            
            AgentResponse response = AgentResponse.builder()
                    .type("summary")
                    .title("学习总结")
                    .summary("已生成「" + topic + "」的学习总结")
                    .content(summary.trim())
                    .build();
            
            return ToolResult.success(response.toJson());
        } catch (Exception e) {
            return ToolResult.failure("生成总结失败: " + e.getMessage());
        }
    }
}
