package com.lqragent.backend.agents.serve.recommendation.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetRecommendationTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_recommendation"; }
    
    @Override
    public String description() { return "# 个性化推荐智能体"; }
    
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
            
            // 个性化推荐
            java.util.List<Map<String, Object>> recommendations = new java.util.ArrayList<>();
            recommendations.add(Map.of("type", "lesson", "title", "Python 基础入门", "reason", "适合初学者"));
            recommendations.add(Map.of("type", "quiz", "title", "变量练习", "reason", "巩固基础"));
            
            Map<String, Object> result = Map.of(
                    "userId", userId,
                    "recommendations", recommendations,
                    "total", recommendations.size(),
                    "summary", "为您推荐 " + recommendations.size() + " 个学习资源"
            );

            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("recommendation 执行失败: " + e.getMessage());
        }
    }
}
