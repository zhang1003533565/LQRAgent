package com.lqragent.backend.agents.resourcegeneration;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.resourcegeneration.tools.GenerateResourceTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class ResourceAgent extends BaseAgent {

    private final GenerateResourceTool tool;

    public ResourceAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                         GenerateResourceTool tool, AgentRegistry agentRegistry,
                         PromptService promptService) {
        super(AgentIds.RESOURCE, llmClient, toolRegistry, agentRegistry, promptService);
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
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请生成学习资源: %s", request.goal());
    }
}
