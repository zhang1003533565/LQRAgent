package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.qaagent.service.QaAgentService;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class QaAgent implements Agent {

    private final QaAgentService qaAgentService;

    @Override
    public String agentId() { return "qa_agent"; }

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
}
