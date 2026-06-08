package com.lqragent.backend.agents.learn.spacedrepetition;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.spacedrepetition.tools.GetReviewScheduleTool;

@Component
public class SpacedRepetitionAgent extends BaseAgent {
    
    private final GetReviewScheduleTool tool;
    
    public SpacedRepetitionAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GetReviewScheduleTool tool, AgentRegistry agentRegistry) {
        super("spaced_repetition_agent", llmClient, toolRegistry, agentRegistry);
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
