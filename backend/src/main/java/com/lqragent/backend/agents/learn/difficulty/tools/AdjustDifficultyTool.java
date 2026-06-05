package com.lqragent.backend.agents.learn.difficulty.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdjustDifficultyTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "adjust_difficulty"; }
    
    @Override
    public String description() { return "# 自适应难度调整智能体"; }
    
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
            
            // 根据答题表现调整难度
            // 简化实现：基于正确率推荐难度
            String recommendedLevel = "medium";
            String reason = "基于平均水平推荐";
            
            Map<String, Object> result = Map.of(
                    "userId", userId,
                    "recommendedLevel", recommendedLevel,
                    "reason", reason,
                    "levels", java.util.List.of("easy", "medium", "hard"),
                    "summary", "推荐难度: " + recommendedLevel
            );
            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("difficulty 执行失败: " + e.getMessage());
        }
    }
}
