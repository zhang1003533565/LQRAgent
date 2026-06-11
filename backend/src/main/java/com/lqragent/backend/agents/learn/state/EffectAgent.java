package com.lqragent.backend.agents.learn.state;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.state.tools.AnalyzeWeaknessTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class EffectAgent extends BaseAgent {

    private final AnalyzeWeaknessTool analyzeWeaknessTool;

    public EffectAgent(RedisStreamsService streams, LlmClient llmClient,
                        AgentToolRegistry toolRegistry, AnalyzeWeaknessTool analyzeWeaknessTool,
                        PromptService promptService) {
        super(AgentIds.EFFECT, streams, llmClient, toolRegistry, promptService);
        this.analyzeWeaknessTool = analyzeWeaknessTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(analyzeWeaknessTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String userId = (String) request.getContent().getOrDefault("userId", "0");
        return String.format("请分析用户 %s 的学习效果和薄弱点。", userId);
    }
}
