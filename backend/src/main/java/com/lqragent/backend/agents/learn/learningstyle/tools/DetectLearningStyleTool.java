package com.lqragent.backend.agents.learn.learningstyle.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DetectLearningStyleTool implements AgentTool {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "detect_learning_style"; }
    
    @Override
    public String description() { return "# 学习风格识别智能体"; }
    
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
            
            // 识别学习风格
            String style = "visual";
            String description = "您倾向于通过图表、视频等视觉方式学习";
            
            Map<String, Object> result = Map.of(
                    "userId", userId,
                    "style", style,
                    "description", description,
                    "recommendations", java.util.List.of("使用思维导图", "观看视频教程"),
                    "summary", "您的学习风格: " + style
            );

            
            return ToolResult.success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            return ToolResult.failure("learningstyle 执行失败: " + e.getMessage());
        }
    }
}
