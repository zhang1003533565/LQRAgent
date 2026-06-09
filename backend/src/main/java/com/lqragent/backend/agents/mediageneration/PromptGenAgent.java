package com.lqragent.backend.agents.mediageneration;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class PromptGenAgent extends BaseAgent {

    public PromptGenAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                          AgentRegistry agentRegistry, PromptService promptService) {
        super(AgentIds.PROMPT_GEN, llmClient, toolRegistry, agentRegistry, promptService);
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of();
    }

    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请为媒体生成编写合适的 Prompt: %s", request.goal());
    }
}
