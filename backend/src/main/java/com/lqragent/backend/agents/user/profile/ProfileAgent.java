package com.lqragent.backend.agents.user.profile;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.user.profile.tools.GetProfileTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.prompt.service.PromptService;

/**
 * 画像构建智能体
 * 分析学生行为数据，提取6维画像特征
 */
@Component
public class ProfileAgent extends BaseAgent {
    
    private final GetProfileTool getProfileTool;
    
    public ProfileAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                        GetProfileTool getProfileTool, AgentRegistry agentRegistry,
                        PromptService promptService) {
        super(AgentIds.PROFILE, llmClient, toolRegistry, agentRegistry, promptService);
        this.getProfileTool = getProfileTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
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
