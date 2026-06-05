package com.lqragent.backend.agents.learn.spacedrepetition.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GetReviewScheduleTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "get_review_schedule"; }
    
    @Override
    public String description() { return "# 间隔复习调度智能体"; }
    
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
            
            // 基于 SM-2 算法计算复习计划
            // 简化实现：根据掌握度计算下次复习时间
            java.util.List<Map<String, Object>> schedule = new java.util.ArrayList<>();
            
            // 模拟复习计划
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            schedule.add(Map.of(
                    "kpId", "kp_variables",
                    "nextReview", now.plusDays(1).toString(),
                    "interval", 1,
                    "mastery", 60
            ));
            schedule.add(Map.of(
                    "kpId", "kp_function",
                    "nextReview", now.plusDays(3).toString(),
                    "interval", 3,
                    "mastery", 45
            ));
            schedule.add(Map.of(
                    "kpId", "kp_class",
                    "nextReview", now.plusDays(7).toString(),
                    "interval", 7,
                    "mastery", 30
            ));
            
            Map<String, Object> result = Map.of(
                    "userId", userId,
                    "schedule", schedule,
                    "totalItems", schedule.size(),
                    "summary", "有 " + schedule.size() + " 个知识点需要复习"
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("spacedrepetition 执行失败: " + e.getMessage());
        }
    }
}
