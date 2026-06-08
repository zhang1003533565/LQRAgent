package com.lqragent.backend.agents.content.summarygen;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.content.summarygen.tools.GenerateSummaryTool;

@Component
public class SummaryAgent extends BaseAgent {
    
    private final GenerateSummaryTool tool;
    
    public SummaryAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                       GenerateSummaryTool tool, AgentRegistry agentRegistry) {
        super("summary_agent", llmClient, toolRegistry, agentRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.summarygen.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 summarygen 相关任务: %s", request.goal());
    }
}
