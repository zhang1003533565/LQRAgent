package com.lqragent.backend.agents.difficulty;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.difficulty.tools.AdjustDifficultyTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class DifficultyAgent extends BaseAgent {

    private final AdjustDifficultyTool tool;

    public DifficultyAgent(RedisStreamsService streams, LlmClient llmClient,
                            AgentToolRegistry toolRegistry, AdjustDifficultyTool tool,
                            PromptService promptService) {
        super(AgentIds.DIFFICULTY, streams, llmClient, toolRegistry, promptService);
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
        return String.format("请执行 difficulty 相关任务: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.DIFFICULTY,
                "难度调节",
                "根据学习者水平调整内容难度、题目难度与学习节奏",
                List.of("difficulty", "level", "难度", "adjust"),
                List.of(ToolSpec.of("adjust_difficulty", "调整难度")),
                List.of("profile", "text"),
                List.of("profile"),
                1, 20000L
        );
    }
}
