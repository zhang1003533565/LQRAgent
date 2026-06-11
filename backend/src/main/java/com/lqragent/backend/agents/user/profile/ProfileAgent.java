package com.lqragent.backend.agents.user.profile;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.user.profile.tools.GetProfileTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

/**
 * 画像构建智能体
 * 分析学生行为数据，提取6维画像特征
 */
@Component
public class ProfileAgent extends BaseAgent {

    private final GetProfileTool getProfileTool;

    public ProfileAgent(RedisStreamsService streams, LlmClient llmClient,
                         AgentToolRegistry toolRegistry, GetProfileTool getProfileTool,
                         PromptService promptService) {
        super(AgentIds.PROFILE, streams, llmClient, toolRegistry, promptService);
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
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String userId = (String) request.getContent().getOrDefault("userId", "0");
        String goal = (String) request.getContent().getOrDefault("goal", "");
        return String.format(
                "请分析用户 %s 的学习画像。先获取现有画像数据，然后根据学习目标「%s」进行分析和更新。",
                userId, goal
        );
    }
}
