package com.lqragent.backend.agents.serve.qa;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.serve.qa.tools.SearchKnowledgeTool;
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
        return String.format("学生提问：%s\n\n请用中文回答。如果知识库有相关内容就使用，否则基于通用知识回答。", request.goal());
    }
}
