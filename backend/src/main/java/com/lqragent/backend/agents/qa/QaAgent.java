package com.lqragent.backend.agents.qa;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.qa.tools.SearchKnowledgeTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class QaAgent extends BaseAgent {
    
    private final SearchKnowledgeTool searchKnowledgeTool;
    
    public QaAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                   SearchKnowledgeTool searchKnowledgeTool) {
        super(AgentIds.QA, llmClient, toolRegistry);
        this.searchKnowledgeTool = searchKnowledgeTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/qa/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(searchKnowledgeTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("学生提问：%s\n\n请搜索知识库并给出详细的回答。", request.goal());
    }
}
