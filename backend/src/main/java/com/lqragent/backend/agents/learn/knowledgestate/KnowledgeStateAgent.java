package com.lqragent.backend.agents.learn.knowledgestate;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.learn.knowledgestate.tools.GetKnowledgeStateTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KnowledgeStateAgent extends BaseAgent {
    
    private final GetKnowledgeStateTool getKnowledgeStateTool;
    
    public KnowledgeStateAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                               GetKnowledgeStateTool getKnowledgeStateTool) {
        super("knowledge_state_agent", llmClient, toolRegistry);
        this.getKnowledgeStateTool = getKnowledgeStateTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/knowledgestate/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(getKnowledgeStateTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        String userId = request.context() != null
                ? request.context().getOrDefault("userId", "0").toString()
                : "0";
        return String.format("分析用户 %s 的知识状态，识别薄弱知识点并给出改进建议。", userId);
    }
}
