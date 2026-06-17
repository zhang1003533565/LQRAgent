package com.lqragent.backend.agents.contentanalysis;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class ContentAnalysisAgent extends BaseAgent {

    public ContentAnalysisAgent(RedisStreamsService streams, LlmClient llmClient,
                                AgentToolRegistry toolRegistry, PromptService promptService) {
        super(AgentIds.CONTENT_ANALYSIS, streams, llmClient, toolRegistry, promptService);
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
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        return String.format("请分析以下内容: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.CONTENT_ANALYSIS,
                "内容分析",
                "分析文本/文档，提取知识点、关键概念、内容结构",
                List.of("analyze", "content_analysis", "extract", "knowledge_point"),
                List.of(),
                List.of("text"),
                List.of("text"),
                1, 30000L
        );
    }
}
