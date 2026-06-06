package com.lqragent.backend.agents.content.summarygen.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateSummaryTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_summary"; }
    
    @Override
    public String description() { return "# 总结生成智能体"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of("type", "integer", "description", "用户ID")
                ),
                "required", new String[]{"userId"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            Long userId = Long.parseLong(args.get("userId").toString());
            
            // 生成总结
            String topic = args.get("topic") != null ? args.get("topic").toString() : "学习内容";
            String summary = "# " + topic + " 总结\n\n## 核心要点\n1. 理解概念\n2. 掌握语法\n3. 实践应用";
            
            Map<String, Object> result = Map.of(
                    "topic", topic,
                    "summary", summary,
                    "format", "markdown",
                    "summaryBrief", "已生成 " + topic + " 的学习总结"
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("summarygen 执行失败: " + e.getMessage());
        }
    }
}
