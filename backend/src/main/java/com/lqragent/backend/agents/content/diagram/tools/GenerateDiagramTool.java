package com.lqragent.backend.agents.content.diagram.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GenerateDiagramTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_diagram"; }
    
    @Override
    public String description() { return "# 图表生成智能体"; }
    
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
            
            // 生成图表
            String topic = args.get("topic") != null ? args.get("topic").toString() : "学习路径";
            String diagram = "graph TD\n    A[" + topic + "] --> B[基础]\n    A --> C[进阶]\n    B --> D[实践]\n    C --> D";
            
            Map<String, Object> result = Map.of(
                    "topic", topic,
                    "diagram", diagram,
                    "format", "mermaid",
                    "summary", "已生成 " + topic + " 的学习路径图"
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("diagram 执行失败: " + e.getMessage());
        }
    }
}
