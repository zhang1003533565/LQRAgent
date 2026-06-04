package com.lqragent.backend.agents.profile;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.profile.tools.GetProfileTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 画像构建智能体
 * 分析学生行为数据，提取6维画像特征
 */
@Component
public class ProfileAgent extends BaseAgent {
    
    private final GetProfileTool getProfileTool;
    
    public ProfileAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                        GetProfileTool getProfileTool) {
        super(AgentIds.PROFILE, llmClient, toolRegistry);
        this.getProfileTool = getProfileTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/profile/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(getProfileTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        Long userId = request.context() != null 
                ? Long.parseLong(request.context().getOrDefault("userId", "0").toString())
                : 0L;
        return String.format(
                "请分析用户 %d 的学习画像。先获取现有画像数据，然后根据学习目标「%s」进行分析和更新。",
                userId, request.goal()
        );
    }
}
