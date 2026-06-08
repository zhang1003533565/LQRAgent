package com.lqragent.backend.agents.learn.difficulty;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.difficulty.tools.AdjustDifficultyTool;

@Component
public class DifficultyAgent extends BaseAgent {
    
    private final AdjustDifficultyTool tool;
    
    public DifficultyAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                       AdjustDifficultyTool tool, AgentRegistry agentRegistry) {
        super("difficulty_agent", llmClient, toolRegistry, agentRegistry);
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
