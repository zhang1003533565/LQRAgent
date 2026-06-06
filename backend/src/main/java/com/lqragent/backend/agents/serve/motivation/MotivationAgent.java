package com.lqragent.backend.agents.serve.motivation;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.serve.motivation.tools.GetMotivationTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MotivationAgent extends BaseAgent {
    
    private final GetMotivationTool tool;
    
    public MotivationAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GetMotivationTool tool) {
        super("motivation_agent", llmClient, toolRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.motivation.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 motivation 相关任务: %s", request.goal());
    }
}
