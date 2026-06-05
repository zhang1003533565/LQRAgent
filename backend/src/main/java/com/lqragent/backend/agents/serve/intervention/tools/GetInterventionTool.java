package com.lqragent.backend.agents.serve.intervention.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetInterventionTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_intervention"; }
    
    @Override
    public String description() { return "# 学习干预智能体"; }
    
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
            
            // 学习干预
            java.util.List<Map<String, Object>> interventions = new java.util.ArrayList<>();
            interventions.add(Map.of("type", "review", "suggestion", "建议复习 Python 基础"));
            interventions.add(Map.of("type", "practice", "suggestion", "多做函数练习"));
            
            Map<String, Object> result = Map.of(
                    "userId", userId,
                    "interventions", interventions,
                    "summary", "发现 " + interventions.size() + " 个需要干预的问题"
            );

            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("intervention 执行失败: " + e.getMessage());
        }
    }
}
