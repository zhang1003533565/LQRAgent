package com.lqragent.backend.agents.learn.knowledgestate;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.knowledgestate.tools.GetKnowledgeStateTool;

@Component
public class KnowledgeStateAgent extends BaseAgent {
    
    private final GetKnowledgeStateTool getKnowledgeStateTool;
    
    public KnowledgeStateAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                               GetKnowledgeStateTool getKnowledgeStateTool, AgentRegistry agentRegistry) {
        super("knowledge_state_agent", llmClient, toolRegistry, agentRegistry);
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
