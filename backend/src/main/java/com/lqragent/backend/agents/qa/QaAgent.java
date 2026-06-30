package com.lqragent.backend.agents.qa;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.base.RagSearchTool;
import com.lqragent.backend.agents.base.StreamSink;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class QaAgent extends BaseAgent {

    private final RagSearchTool ragSearchTool;

    public QaAgent(RedisStreamsService streams, LlmClient llmClient,
                    AgentToolRegistry toolRegistry, RagSearchTool ragSearchTool,
                    PromptService promptService) {
        super(AgentIds.QA, streams, llmClient, toolRegistry, promptService);
        this.ragSearchTool = ragSearchTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(ragSearchTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    /**
     * 带对话历史的处理
     */
    public AgentMessage processWithHistory(AgentMessage request, List<Map<String, Object>> history) {
        return executeLlmLoop(request, history);
    }

    /**
     * 带对话历史的流式处理（QA 直出）。
     */
    public AgentMessage streamWithHistory(AgentMessage request, List<Map<String, Object>> history, StreamSink sink) {
        String userMessage = buildUserMessage(request);
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        var response = llmClient.chatStream(getSystemPrompt(), messages, sink);
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("content", response.content());
        content.put("status", "completed");
        if (!response.isSuccess()) {
            return AgentMessage.error(request.getTaskId(), AgentIds.QA, response.error());
        }
        return AgentMessage.inform(request.getTaskId(), AgentIds.QA, "pipeline", content);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        StringBuilder sb = new StringBuilder();
        sb.append("学生提问：").append(goal).append("\n\n");
        sb.append("请用中文回答。如果知识库有相关内容就使用，否则基于通用知识回答。");
        sb.append("如果有用户画像信息，请根据用户的薄弱点和学习偏好进行针对性回答。");
        return sb.toString();
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.QA,
                "智能问答",
                "解答学习问题、知识检索、概念解释、生成讲解文本",
                List.of("qa", "question", "answer", "explain", "knowledge"),
                List.of(ToolSpec.of("search_knowledge", "知识库检索")),
                List.of("text", "rag_sources"),
                List.of("text", "rag_sources"),
                1, 30000L
        );
    }
}
