package com.lqragent.backend.agents.mediageneration;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.mediageneration.tools.GenerateMediaTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class MediaGenAgent extends BaseAgent {

    private final GenerateMediaTool tool;

    public MediaGenAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                         GenerateMediaTool tool, AgentRegistry agentRegistry,
                         PromptService promptService) {
        super(AgentIds.MEDIA_GEN, llmClient, toolRegistry, agentRegistry, promptService);
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
        return String.format("请生成媒体内容: %s", request.goal());
    }
}
