package com.lqragent.backend.agents.learn.spacedrepetition;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.spacedrepetition.tools.GetReviewScheduleTool;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class SpacedRepetitionAgent extends BaseAgent {

    private final GetReviewScheduleTool tool;

    public SpacedRepetitionAgent(RedisStreamsService streams, LlmClient llmClient,
                                  AgentToolRegistry toolRegistry, GetReviewScheduleTool tool,
                                  PromptService promptService) {
        super("spaced_repetition_agent", streams, llmClient, toolRegistry, promptService);
        this.tool = tool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        return String.format("请执行 spacedrepetition 相关任务: %s", goal);
    }
}
