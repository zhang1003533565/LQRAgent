package com.lqragent.backend.agents.learn.path;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.path.tools.GeneratePathTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class LearningPathAgent extends BaseAgent {

    private final GeneratePathTool generatePathTool;

    public LearningPathAgent(RedisStreamsService streams, LlmClient llmClient,
                              AgentToolRegistry toolRegistry, GeneratePathTool generatePathTool,
                              PromptService promptService) {
        super(AgentIds.LEARNING_PATH, streams, llmClient, toolRegistry, promptService);
        this.generatePathTool = generatePathTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(generatePathTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String userId = (String) request.getContent().getOrDefault("userId", "0");
        String goal = (String) request.getContent().getOrDefault("goal", "");
        StringBuilder sb = new StringBuilder();
        sb.append("请为用户 ").append(userId).append(" 生成学习路径，目标：").append(goal).append("\n\n");
        return sb.toString();
    }
}
