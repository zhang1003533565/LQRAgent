package com.lqragent.backend.agents.learningpath.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.learningpath.service.LearningPathService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeneratePathTool implements AgentTool {
    
    private final LearningPathService pathService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() { return "generate_path"; }
    
    @Override
    public String description() { return "根据学习目标生成个性化学习路径"; }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of("type", "integer", "description", "用户ID"),
                        "goal", Map.of("type", "string", "description", "学习目标")
                ),
                "required", new String[]{"userId", "goal"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            Long userId = Long.parseLong(args.get("userId").toString());
            String goal = args.get("goal").toString();
            var path = pathService.generatePath(userId, goal, null);
            return ToolResult.success(mapper.writeValueAsString(path));
        } catch (Exception e) {
            return ToolResult.failure("生成路径失败: " + e.getMessage());
        }
    }
}
