package com.lqragent.backend.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * Orchestrator WebSocket 端点
 * 前端连接 ws://localhost:8080/ws/orchestrator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorWebSocketHandler extends TextWebSocketHandler {

    private final OrchestratorCore orchestrator;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("[WS] orchestrator connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> payload = mapper.readValue(message.getPayload(), Map.class);
            String type = (String) payload.get("type");

            if ("learn".equals(type)) {
                String goal = (String) payload.get("goal");
                String userId = (String) payload.get("userId");
                log.info("[WS] learn request: goal={}", goal);
                orchestrator.handleLearnRequest(userId, goal, session);
            }
        } catch (Exception e) {
            log.error("[WS] handle error: {}", e.getMessage(), e);
            try {
                session.sendMessage(new TextMessage(
                        mapper.writeValueAsString(Map.of("type", "error", "message", e.getMessage()))));
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("[WS] orchestrator disconnected: {}", session.getId());
    }
}
