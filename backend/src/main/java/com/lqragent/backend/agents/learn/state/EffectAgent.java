package com.lqragent.backend.agents.learn.state;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.state.tools.AnalyzeWeaknessTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class EffectAgent extends BaseAgent {
    
    private final AnalyzeWeaknessTool analyzeWeaknessTool;
    
    public EffectAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                       AnalyzeWeaknessTool analyzeWeaknessTool, AgentRegistry agentRegistry,
                       PromptService promptService) {
        super(AgentIds.EFFECT, llmClient, toolRegistry, agentRegistry, promptService);
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
    protected String buildUserMessage(AgentRequest request) {
        Long userId = request.context() != null
                ? Long.parseLong(request.context().getOrDefault("userId", "0").toString())
                : 0L;
        return String.format("请分析用户 %d 的学习效果和薄弱点。", userId);
    }
}
