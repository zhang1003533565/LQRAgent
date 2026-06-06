package com.lqragent.backend.agents.serve.motivation.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetMotivationTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_motivation"; }
    
    @Override
    public String description() { return "# 激励系统智能体"; }
    
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
            
            // 激励系统
            String message = "每天进步一点点就是成功！";
            java.util.List<String> achievements = java.util.List.of("连续学习 3 天", "完成 10 道题");
            
            Map<String, Object> result = Map.of(
                    "userId", userId,
                    "message", message,
                    "achievements", achievements,
                    "streak", 3,
                    "summary", "你已经连续学习 3 天了！"
            );

            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("motivation 执行失败: " + e.getMessage());
        }
    }
}
