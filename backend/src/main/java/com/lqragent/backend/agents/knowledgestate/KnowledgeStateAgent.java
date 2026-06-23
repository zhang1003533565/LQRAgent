package com.lqragent.backend.agents.knowledgestate;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.knowledgestate.tools.GetKnowledgeStateTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class KnowledgeStateAgent extends BaseAgent {

    private final GetKnowledgeStateTool getKnowledgeStateTool;

    public KnowledgeStateAgent(RedisStreamsService streams, LlmClient llmClient,
                                AgentToolRegistry toolRegistry, GetKnowledgeStateTool getKnowledgeStateTool,
                                PromptService promptService) {
        super(AgentIds.KNOWLEDGE_STATE, streams, llmClient, toolRegistry, promptService);
        this.getKnowledgeStateTool = getKnowledgeStateTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(getKnowledgeStateTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String userId = (String) request.getContent().getOrDefault("userId", "0");
        return String.format("分析用户 %s 的知识状态，识别薄弱知识点并给出改进建议。", userId);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.KNOWLEDGE_STATE,
                "知识状态分析",
                "分析学习者知识掌握度、识别薄弱点并更新知识状态",
                List.of("knowledge_state", "mastery", "weakness", "掌握度"),
                List.of(ToolSpec.of("get_knowledge_state", "获取知识状态")),
                List.of("profile", "text"),
                List.of("weakness_profile", "profile"),
                1, 25000L
        );
    }
}
