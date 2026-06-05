package com.lqragent.backend.agents.serve.intervention;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.serve.intervention.tools.GetInterventionTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterventionAgent extends BaseAgent {
    
    private final GetInterventionTool tool;
    
    public InterventionAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GetInterventionTool tool) {
        super("intervention_agent", llmClient, toolRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.intervention.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 intervention 相关任务: %s", request.goal());
    }
}
