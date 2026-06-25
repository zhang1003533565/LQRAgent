package com.lqragent.backend.agents.qa;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.base.LlmResponse;
import com.lqragent.backend.agents.base.StreamSink;
import com.lqragent.backend.agents.aiserver.tools.AiServerToolFactory;
import com.lqragent.backend.agents.base.RagSearchTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class QaAgent extends BaseAgent {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RagSearchTool ragSearchTool;
    private final AiServerToolFactory aiServerToolFactory;

    public QaAgent(RedisStreamsService streams, LlmClient llmClient,
                    AgentToolRegistry toolRegistry, RagSearchTool ragSearchTool,
                    PromptService promptService, AiServerToolFactory aiServerToolFactory) {
        super(AgentIds.QA, streams, llmClient, toolRegistry, promptService);
        this.ragSearchTool = ragSearchTool;
        this.aiServerToolFactory = aiServerToolFactory;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(ragSearchTool, aiServerToolFactory.deepSolveTool());
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        prefetchRag(request);
        return executeLlmLoop(request);
    }

    private void prefetchRag(AgentMessage request) {
        String goal = String.valueOf(request.getContent().getOrDefault("goal", "")).trim();
        if (goal.isBlank()) {
            return;
        }
        try {
            Map<String, Object> ragArgs = new java.util.LinkedHashMap<>(Map.of("query", goal, "topK", 3));
            Object userId = request.getContent().get("userId");
            if (userId != null) {
                ragArgs.put("userId", userId);
            }
            AgentTool.ToolResult result = ragSearchTool.execute(ragArgs);
            if (!result.success()) {
                return;
            }
            if (result.metadata() != null) {
                Object sources = result.metadata().get("ragSources");
                if (sources instanceof List<?> list && !list.isEmpty()) {
                    request.getContent().put("ragSources", sources);
                }
            }
            if (result.content() != null && !result.content().isBlank()) {
                request.getContent().put("ragContext", extractRagContext(result.content()));
            }
        } catch (Exception e) {
            // 知识库不可用时静默降级，不影响正常问答
        }
    }

    /**
     * 带对话历史的处理
     */
    public AgentMessage processWithHistory(AgentMessage request, List<Map<String, Object>> history) {
        prefetchRag(request);
        return executeLlmLoop(request, history);
    }

    /**
     * 带对话历史的流式处理（RAG 预取后直出 token 流，不走 tool 循环）
     */
    public AgentMessage streamWithHistory(AgentMessage request, List<Map<String, Object>> history, StreamSink sink) {
        prefetchRag(request);
        List<Map<String, Object>> messages = new java.util.ArrayList<>();
        if (history != null) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", buildUserMessage(request)));

        LlmResponse response = llmClient.chatStream(getSystemPrompt(), messages, sink);
        if (!response.isSuccess() || response.content() == null) {
            return AgentMessage.error(request.getTaskId(), getAgentId(), response.error());
        }

        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("content", response.content());
        Object ragSources = request.getContent().get("ragSources");
        if (ragSources != null) {
            content.put("ragSources", ragSources);
        }
        return AgentMessage.inform(request.getTaskId(), getAgentId(), request.getSender(), content);
    }

    private static String extractRagContext(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            String summary = root.path("summary").asText("");
            if (!summary.isBlank()) {
                return summary;
            }
            JsonNode sources = root.path("sources");
            if (sources.isArray() && !sources.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode s : sources) {
                    if (sb.length() > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(s.path("content").asText(s.path("title").asText("")));
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
        }
        return json;
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        StringBuilder sb = new StringBuilder();
        sb.append("学生提问：").append(goal).append("\n\n");
        Object ragContext = request.getContent().get("ragContext");
        if (ragContext != null && !String.valueOf(ragContext).isBlank()) {
            sb.append("知识库检索结果（请优先参考）：\n").append(ragContext).append("\n\n");
        }
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
                List.of("qa", "question", "answer", "explain", "knowledge", "deep_solve"),
                List.of(
                        ToolSpec.of("search_knowledge", "知识库检索"),
                        ToolSpec.of("deep_solve", "复杂问题深度求解")
                ),
                List.of("text", "rag_sources"),
                List.of("text", "rag_sources"),
                1, 30000L
        );
    }
}
