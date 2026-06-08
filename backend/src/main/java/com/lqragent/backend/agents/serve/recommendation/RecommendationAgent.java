package com.lqragent.backend.agents.serve.recommendation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.serve.recommendation.tools.GetRecommendationTool;

@Component
public class RecommendationAgent extends BaseAgent {
    
    private final GetRecommendationTool tool;
    
    public RecommendationAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GetRecommendationTool tool, AgentRegistry agentRegistry) {
        super("recommendation_agent", llmClient, toolRegistry, agentRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.recommendation.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 recommendation 相关任务: %s", request.goal());
    }
}
