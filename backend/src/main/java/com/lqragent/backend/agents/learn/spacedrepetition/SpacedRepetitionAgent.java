package com.lqragent.backend.agents.learn.spacedrepetition;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.learn.spacedrepetition.tools.GetReviewScheduleTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpacedRepetitionAgent extends BaseAgent {
    
    private final GetReviewScheduleTool tool;
    
    public SpacedRepetitionAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GetReviewScheduleTool tool) {
        super("spaced_repetition_agent", llmClient, toolRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.spacedrepetition.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 spacedrepetition 相关任务: %s", request.goal());
    }
}
