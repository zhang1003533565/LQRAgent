package com.lqragent.backend.agents.content.diagram;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.content.diagram.tools.GenerateDiagramTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DiagramAgent extends BaseAgent {
    
    private final GenerateDiagramTool tool;
    
    public DiagramAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GenerateDiagramTool tool) {
        super("diagram_agent", llmClient, toolRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.diagram.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 diagram 相关任务: %s", request.goal());
    }
}
