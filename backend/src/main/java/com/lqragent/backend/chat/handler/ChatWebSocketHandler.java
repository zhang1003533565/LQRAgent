package com.lqragent.backend.chat.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.qaagent.service.QaAgentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /ws/chat WebSocket 端点处理器。
 * <p>
 * 职责：
 * - 管理前端 WebSocket 连接（ConcurrentHashMap）
 * - 接收前端消息，委托 QaAgent 处理（P0 仅答疑通道）
 * - 将 ai-server 的流式响应转换为 WS 事件推给前端
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionService chatSessionService;
    private final QaAgentService qaAgentService;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** wsSessionId → (userId, username) */
    private final Map<String, UserInfo> sessionUsers = new ConcurrentHashMap<>();
    /** wsSessionId → WebSocketSession */
    private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        Long userId = (Long) attrs.get("userId");
        String username = (String) attrs.get("username");

        if (userId == null) {
            log.warn("[WS] connection without userId, closing");
            try { session.close(CloseStatus.NOT_ACCEPTABLE); } catch (IOException ignored) {}
            return;
        }

        sessionUsers.put(session.getId(), new UserInfo(userId, username));
        wsSessions.put(session.getId(), session);
        log.info("[WS] client connected: sessionId={}, userId={}, username={}", session.getId(), userId, username);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UserInfo userInfo = sessionUsers.get(session.getId());
        if (userInfo == null) {
            sendEvent(session, "error", "未认证");
            return;
        }

        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendEvent(session, "error", "消息格式错误");
            return;
        }

        String type = node.has("type") ? node.get("type").asText() : "";
        String content = node.has("content") ? node.get("content").asText() : "";
        String sessionId = node.has("session_id") ? node.get("session_id").asText() : null;

        if (!"message".equals(type) || content.isBlank()) {
            return;
        }

        // Resolve or create chat session
        if (sessionId == null || sessionId.isBlank()) {
            ChatSession chatSession = chatSessionService.createSession(userInfo.userId, generateTitle(content));
            sessionId = chatSession.getId();
            sendEvent(session, "session_created", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .put("title", chatSession.getTitle())
                    .toString());
        } else {
            chatSessionService.findById(sessionId)
                    .orElseGet(() -> chatSessionService.createSession(userInfo.userId, generateTitle(content)));
        }

        // Persist user message
        ChatMessage userMsg = ChatMessage.builder()
                .userId(userInfo.userId)
                .sessionId(sessionId)
                .sender(ChatMessage.Sender.USER)
                .contentType(ChatMessage.ContentType.TEXT)
                .body(content)
                .build();
        chatMessageRepository.save(userMsg);

        // Push agent_step: qa_agent running
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "qa_agent")
                .put("label", "正在理解问题...")
                .put("status", "running")
                .toString());

        final String finalSessionId = sessionId;
        StringBuilder fullResponse = new StringBuilder();

        // Delegate to QaAgent → ai-server
        qaAgentService.handleMessage(userInfo.userId, finalSessionId, content, new AiServerWsProxy.StreamCallback() {
            @Override
            public void onChunk(String chunk) {
                fullResponse.append(chunk);
                sendEvent(session, "chunk", chunk);
            }

            @Override
            public void onDone(String aiServerSessionId) {
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "qa_agent")
                        .put("label", "回答完成")
                        .put("status", "done")
                        .toString());

                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());

                // Persist AI response
                ChatMessage aiMsg = ChatMessage.builder()
                        .userId(userInfo.userId)
                        .sessionId(finalSessionId)
                        .sender(ChatMessage.Sender.AI)
                        .contentType(ChatMessage.ContentType.TEXT)
                        .body(fullResponse.toString())
                        .build();
                chatMessageRepository.save(aiMsg);
            }

            @Override
            public void onError(String error) {
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "qa_agent")
                        .put("label", "处理失败")
                        .put("status", "failed")
                        .put("detail", error)
                        .toString());

                sendEvent(session, "error", error);
            }
        });
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionUsers.remove(session.getId());
        wsSessions.remove(session.getId());
        log.info("[WS] client disconnected: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] transport error: sessionId={}, msg={}", session.getId(), exception.getMessage());
        sessionUsers.remove(session.getId());
        wsSessions.remove(session.getId());
    }

    private void sendEvent(WebSocketSession session, String type, String content) {
        try {
            String payload;
            if (type.equals("chunk")) {
                // Simple chunk format: just the text content as JSON with type
                payload = objectMapper.createObjectNode()
                        .put("type", type)
                        .put("content", content)
                        .toString();
            } else {
                // Other events: content is already a JSON string or plain text
                try {
                    JsonNode contentNode = objectMapper.readTree(content);
                    payload = objectMapper.createObjectNode()
                            .put("type", type)
                            .setAll((com.fasterxml.jackson.databind.node.ObjectNode) contentNode)
                            .toString();
                } catch (Exception e) {
                    payload = objectMapper.createObjectNode()
                            .put("type", type)
                            .put("content", content)
                            .toString();
                }
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.error("[WS] send error: sessionId={}, type={}, msg={}", session.getId(), type, e.getMessage());
        }
    }

    private String generateTitle(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 30 ? cleaned.substring(0, 30) + "..." : cleaned;
    }

    private record UserInfo(Long userId, String username) {}
}
