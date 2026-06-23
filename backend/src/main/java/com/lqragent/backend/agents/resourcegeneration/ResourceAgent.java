package com.lqragent.backend.agents.resourcegeneration;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.resourcegeneration.tools.GenerateResourceTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class ResourceAgent extends BaseAgent {

    private final GenerateResourceTool tool;

    public ResourceAgent(RedisStreamsService streams, LlmClient llmClient,
                          AgentToolRegistry toolRegistry, GenerateResourceTool tool,
                          PromptService promptService) {
        super(AgentIds.RESOURCE, streams, llmClient, toolRegistry, promptService);
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
        return String.format("请生成学习资源: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.RESOURCE,
                "学习资源生成",
                "生成讲义、练习题、代码示例、多卡片学习资源包",
                List.of("resource", "lesson", "quiz", "content", "资源"),
                List.of(ToolSpec.of("generate_resource", "生成学习资源")),
                List.of("text"),
                List.of("lesson", "quiz", "multi_card", "text"),
                1, 60000L
        );
    }
}
