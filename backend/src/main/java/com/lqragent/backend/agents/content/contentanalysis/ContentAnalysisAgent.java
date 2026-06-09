package com.lqragent.backend.agents.content.contentanalysis;

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
public class ContentAnalysisAgent extends BaseAgent {

    public ContentAnalysisAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                AgentRegistry agentRegistry, PromptService promptService) {
        super(AgentIds.CONTENT_ANALYSIS, llmClient, toolRegistry, agentRegistry, promptService);
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
        return String.format("请分析以下内容: %s", request.goal());
    }
}
