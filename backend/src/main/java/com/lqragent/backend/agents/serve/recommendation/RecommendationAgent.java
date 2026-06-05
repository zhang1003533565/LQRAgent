package com.lqragent.backend.agents.serve.recommendation;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.serve.recommendation.tools.GetRecommendationTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RecommendationAgent extends BaseAgent {
    
    private final GetRecommendationTool tool;
    
    public RecommendationAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GetRecommendationTool tool) {
        super("recommendation_agent", llmClient, toolRegistry);
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
