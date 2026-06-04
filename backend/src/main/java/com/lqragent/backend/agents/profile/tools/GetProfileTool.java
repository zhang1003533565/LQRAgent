package com.lqragent.backend.agents.profile.tools;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 获取用户画像工具
 */
@Component
@RequiredArgsConstructor
public class GetProfileTool implements AgentTool {
    
    private final LearnerProfileService profileService;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public String name() {
        return "get_user_profile";
    }
    
    @Override
    public String description() {
        return "获取用户的现有画像数据，包括知识水平、学习目标、薄弱点等";
    }
    
    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "userId", Map.of(
                                "type", "integer",
                                "description", "用户ID"
                        )
                ),
                "required", new String[]{"userId"}
        );
    }
    
    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            Long userId = Long.parseLong(args.get("userId").toString());
            var summary = profileService.getSummary(userId);
            String json = mapper.writeValueAsString(summary);
            return ToolResult.success(json);
        } catch (Exception e) {
            return ToolResult.failure("获取画像失败: " + e.getMessage());
        }
    }
}
