package com.lqragent.backend.agents.intelligentqa;

import com.lqragent.backend.core.agent.Agent;
import com.lqragent.backend.core.agent.AgentIds;
import com.lqragent.backend.core.agent.AgentResult;
import com.lqragent.backend.core.agent.AgentTask;
import com.lqragent.backend.core.tool.ToolRegistry;
import com.lqragent.backend.core.tool.ToolSchema;
import com.lqragent.backend.agents.intelligentqa.service.QaAgentService;
import com.lqragent.backend.agents.intelligentqa.service.MermaidGenerator;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class IntelligentQaAgent implements Agent {

    private final QaAgentService qaAgentService;
    private final MermaidGenerator mermaidGenerator;

    @Override
    public String agentId() { return AgentIds.INTELLIGENT_QA; }

    @Override
    public AgentResult process(AgentTask task) {
        String message = (String) task.getPayload().getOrDefault("message", "");
        Long userId = task.getUserId();
        String sessionId = task.getSessionId();

        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder fullResponse = new StringBuilder();

        qaAgentService.handleMessage(userId, sessionId, message, new AiServerWsProxy.StreamCallback() {
            @Override
            public void onChunk(String chunk) { fullResponse.append(chunk); }

            @Override
            public void onDone(String aiServerSessionId) { future.complete(fullResponse.toString()); }

            @Override
            public void onError(String error) { future.completeExceptionally(new RuntimeException(error)); }
        });

        try {
            String response = future.get(60, TimeUnit.SECONDS);
            return AgentResult.builder().success(true).data(Map.of("response", response)).build();
        } catch (Exception e) {
            return AgentResult.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    @Override
    public String getSystemPrompt(AgentTask task) {
        return "你是智能辅导助手。先用 ai-server 获取回答，再判断是否需要生成 Mermaid 流程图。";
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("answer", "流式回答用户问题",
                ToolSchema.params(Map.of(
                    "question", ToolSchema.stringParam("问题", "用户提问内容"),
                    "sessionId", ToolSchema.stringParam("会话ID", "")
                ), "question")),
            ToolSchema.of("generate_mermaid", "生成 Mermaid 流程图",
                ToolSchema.params(Map.of(
                    "question", ToolSchema.stringParam("问题", ""),
                    "answer", ToolSchema.stringParam("回答文本", "LLM 的文字回答")
                ), "question", "answer"))
        );
    }

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register(agentId(), "answer", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            return Map.of("note", "QA 回答走 WS 流式通道，此处仅做标记");
        });
        registry.register(agentId(), "generate_mermaid", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            String q = (String) p.getOrDefault("question", "");
            String a = (String) p.getOrDefault("answer", "");
            String mermaid = mermaidGenerator.generate(q, a);
            return Map.of("mermaid", mermaid != null ? mermaid : "");
        });
    }
}
