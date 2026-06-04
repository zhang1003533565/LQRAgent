package com.lqragent.backend.agents.quality;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.quality.tools.CheckQualityTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QualityAgent extends BaseAgent {
    
    private final CheckQualityTool checkQualityTool;
    
    public QualityAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                        CheckQualityTool checkQualityTool) {
        super(AgentIds.QUALITY, llmClient, toolRegistry);
        this.checkQualityTool = checkQualityTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/quality/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(checkQualityTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        String resourceIds = request.context() != null
                ? request.context().getOrDefault("resourceIds", "").toString()
                : "";
        return String.format("请检查以下资源的质量：\n%s", resourceIds);
    }
}
