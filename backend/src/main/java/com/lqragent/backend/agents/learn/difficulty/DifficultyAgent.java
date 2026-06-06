package com.lqragent.backend.agents.learn.difficulty;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.learn.difficulty.tools.AdjustDifficultyTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DifficultyAgent extends BaseAgent {
    
    private final AdjustDifficultyTool tool;
    
    public DifficultyAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   AdjustDifficultyTool tool) {
        super("difficulty_agent", llmClient, toolRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.difficulty.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 difficulty 相关任务: %s", request.goal());
    }
}
