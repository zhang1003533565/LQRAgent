package com.lqragent.backend.chat.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agent.AgentBus;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
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

/**
 * /ws/chat WebSocket 端点处理器。
 * <p>
 * 职责：
 * - 管理前端 WebSocket 连接
 * - Orchestrator 意图识别 → 路由到对应智能体
 * - 流式响应转发给前端
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionService chatSessionService;
    private final QaAgentService qaAgentService;
    private final AgentBus agentBus;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        sessionManager.register(session, userId, username);
        log.info("[WS] client connected: sessionId={}, userId={}, username={}", session.getId(), userId, username);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        var userInfo = sessionManager.getUserInfo(session.getId());
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
            ChatSession chatSession = chatSessionService.createSession(userInfo.userId(), generateTitle(content));
            sessionId = chatSession.getId();
            sendEvent(session, "session_created", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .put("title", chatSession.getTitle())
                    .toString());
        } else {
            chatSessionService.findById(sessionId)
                    .orElseGet(() -> chatSessionService.createSession(userInfo.userId(), generateTitle(content)));
        }

        // Persist user message
        ChatMessage userMsg = ChatMessage.builder()
                .userId(userInfo.userId())
                .sessionId(sessionId)
                .sender(ChatMessage.Sender.USER)
                .contentType(ChatMessage.ContentType.TEXT)
                .body(content)
                .build();
        chatMessageRepository.save(userMsg);

        final String finalSessionId = sessionId;

        // Orchestrator: 通过 AgentBus 识别意图
        AgentTask intentTask = AgentTask.builder()
                .agentType("orchestrator")
                .userId(userInfo.userId())
                .sessionId(finalSessionId)
                .payload(Map.of("message", content))
                .build();
        AgentResult intentResult = agentBus.dispatch(intentTask).join();
        String intent = (String) intentResult.getData().getOrDefault("intent", "qa_question");
        log.info("[WS] intent={} (via AgentBus)", intent);

        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "正在分析问题...")
                .put("intent", intent)
                .put("status", "running")
                .toString());

        // 非 QA 意图 → 通过 AgentBus 路由到对应 agent
        if (!"qa_question".equals(intent) && !"unknown".equals(intent)) {
            AgentTask agentTask = AgentTask.builder()
                    .agentType("orchestrator")
                    .userId(userInfo.userId())
                    .sessionId(finalSessionId)
                    .payload(Map.of("intent", intent, "message", content))
                    .build();
            // 暂由 orchestrator agent 回复文本指引，后续改为路由到具体 agent
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "已识别 " + intentResult.getData().getOrDefault("label", intent))
                    .put("intent", intent)
                    .put("status", "done")
                    .toString());
            sendEvent(session, "chunk", "已识别您的需求。请使用对应 REST API 完成操作。");
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", finalSessionId)
                    .toString());
            return;
        }

        // QA + unknown → 走答疑通道 (保留流式)
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "qa_agent")
                .put("label", "正在理解问题...")
                .put("status", "running")
                .toString());

                StringBuilder fullResponse = new StringBuilder();
                final Long currentUserId = userInfo.userId();
                qaAgentService.handleMessage(currentUserId, finalSessionId, content, new AiServerWsProxy.StreamCallback() {
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
                        persistAiMessage(userInfo.userId(), finalSessionId, fullResponse.toString());

                        // P2-5: artifact multi_card — 检测响应含媒体时推送
                        try {
                            var artifactPayload = buildMultiCardIfMedia(fullResponse.toString());
                            if (artifactPayload != null) {
                                var artifactMsg = objectMapper.createObjectNode()
                                        .put("type", "artifact")
                                        .put("kind", "multi_card")
                                        .put("session_id", finalSessionId)
                                        .set("payload", artifactPayload);
                                synchronized (session) {
                                    session.sendMessage(new TextMessage(artifactMsg.toString()));
                                }
                            }
                        } catch (Exception e) {
                            log.warn("[WS] artifact push failed", e);
                        }
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
        sessionManager.unregister(session.getId());
        log.info("[WS] client disconnected: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("[WS] transport error: sessionId={}, msg={}", session.getId(), exception.getMessage());
        sessionManager.unregister(session.getId());
    }

    private void sendEvent(WebSocketSession session, String type, String content) {
        try {
            String payload;
            if (type.equals("chunk")) {
                payload = objectMapper.createObjectNode()
                        .put("type", type)
                        .put("content", content)
                        .toString();
            } else {
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

    private void persistAiMessage(Long userId, String sessionId, String body) {
        ChatMessage aiMsg = ChatMessage.builder()
                .userId(userId)
                .sessionId(sessionId)
                .sender(ChatMessage.Sender.AI)
                .contentType(ChatMessage.ContentType.TEXT)
                .body(body)
                .build();
        chatMessageRepository.save(aiMsg);
    }

    private String generateTitle(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 30 ? cleaned.substring(0, 30) + "..." : cleaned;
    }

    /**
     * 检测 AI 响应是否含媒体内容，返回 multi_card payload 或 null。
     * P2-5：简单检测 markdown 图片语法和 <img> 标签。
     */
    private com.fasterxml.jackson.databind.JsonNode buildMultiCardIfMedia(String response) {
        if (response == null || response.isBlank()) return null;

        java.util.regex.Matcher imgMatcher = java.util.regex.Pattern.compile(
                "!\\[([^]]*)\\]\\(([^)]+)\\)"
        ).matcher(response);

        boolean hasImage = imgMatcher.find();
        boolean hasHtmlImage = response.contains("<img") || response.contains("<video");

        if (!hasImage && !hasHtmlImage) return null;

        var cards = objectMapper.createArrayNode();
        cards.add(objectMapper.createObjectNode()
                .put("type", "text")
                .put("content", response));

        if (hasImage) {
            imgMatcher.reset();
            while (imgMatcher.find()) {
                cards.add(objectMapper.createObjectNode()
                        .put("type", "image")
                        .put("alt", imgMatcher.group(1))
                        .put("url", imgMatcher.group(2)));
            }
        }

        return cards;
    }

}
