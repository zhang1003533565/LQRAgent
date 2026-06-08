package com.lqragent.backend.agents.content.diagram;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.content.diagram.tools.GenerateDiagramTool;

@Component
public class DiagramAgent extends BaseAgent {
    
    private final GenerateDiagramTool tool;
    
    public DiagramAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                       GenerateDiagramTool tool, AgentRegistry agentRegistry) {
        super("diagram_agent", llmClient, toolRegistry, agentRegistry);
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
