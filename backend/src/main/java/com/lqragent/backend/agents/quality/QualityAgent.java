package com.lqragent.backend.agents.quality;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.quality.tools.CheckQualityTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class QualityAgent extends BaseAgent {

    private final CheckQualityTool checkQualityTool;

    public QualityAgent(RedisStreamsService streams, LlmClient llmClient,
                         AgentToolRegistry toolRegistry, CheckQualityTool checkQualityTool,
                         PromptService promptService) {
        super(AgentIds.QUALITY, streams, llmClient, toolRegistry, promptService);
        this.checkQualityTool = checkQualityTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(checkQualityTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String resourceIds = (String) request.getContent().getOrDefault("resourceIds", "");
        StringBuilder sb = new StringBuilder();
        sb.append("请检查以下资源的质量：\n").append(resourceIds).append("\n\n");
        sb.append("如果发现质量问题，请说明具体问题并提供建议。");
        return sb.toString();
    }
}
