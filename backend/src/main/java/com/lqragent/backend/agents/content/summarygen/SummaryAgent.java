package com.lqragent.backend.agents.content.summarygen;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.content.summarygen.tools.GenerateSummaryTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SummaryAgent extends BaseAgent {
    
    private final GenerateSummaryTool tool;
    
    public SummaryAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GenerateSummaryTool tool) {
        super("summary_agent", llmClient, toolRegistry);
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
