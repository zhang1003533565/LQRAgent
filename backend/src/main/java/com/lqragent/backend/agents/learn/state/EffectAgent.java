package com.lqragent.backend.agents.learn.state;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.learn.state.tools.AnalyzeWeaknessTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EffectAgent extends BaseAgent {
    
    private final AnalyzeWeaknessTool analyzeWeaknessTool;
    
    public EffectAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                       AnalyzeWeaknessTool analyzeWeaknessTool) {
        super(AgentIds.EFFECT, llmClient, toolRegistry);
        this.analyzeWeaknessTool = analyzeWeaknessTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/effect/prompts/system.md");
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
