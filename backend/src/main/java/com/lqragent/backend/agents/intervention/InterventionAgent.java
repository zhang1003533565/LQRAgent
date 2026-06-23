package com.lqragent.backend.agents.intervention;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.intervention.tools.GetInterventionTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class InterventionAgent extends BaseAgent {

    private final GetInterventionTool tool;

    public InterventionAgent(RedisStreamsService streams, LlmClient llmClient,
                              AgentToolRegistry toolRegistry, GetInterventionTool tool,
                              PromptService promptService) {
        super(AgentIds.INTERVENTION, streams, llmClient, toolRegistry, promptService);
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
        StringBuilder sb = new StringBuilder();
        sb.append("请执行干预分析任务：").append(goal).append("\n\n");
        return sb.toString();
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.INTERVENTION,
                "学习干预",
                "分析学习状态异常、给出干预建议并调整学习路径",
                List.of("intervention", "suggest", "干预", "assess_and_intervene"),
                List.of(ToolSpec.of("get_intervention", "获取干预建议")),
                List.of("profile", "text"),
                List.of("text", "learning_path"),
                1, 30000L
        );
    }
}
