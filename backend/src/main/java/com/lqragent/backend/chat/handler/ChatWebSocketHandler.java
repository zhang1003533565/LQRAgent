package com.lqragent.backend.chat.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.orchestrator.dto.IntentResult;
import com.lqragent.backend.orchestrator.service.OrchestratorService;
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
    private final OrchestratorService orchestratorService;
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

        // Orchestrator: 识别意图
        IntentResult intent = orchestratorService.determineIntent(content);
        log.info("[WS] intent={} label={} confidence={}", intent.getIntent(), intent.getLabel(), intent.getConfidence());

        // 推送 agent_step: orchestrator routing
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "正在分析问题...")
                .put("intent", intent.getIntent())
                .put("status", "running")
                .toString());

        final String finalSessionId = sessionId;

        // 按意图路由
        switch (intent.getIntent()) {
            case IntentResult.LEARNING_PATH -> {
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "orchestrator")
                        .put("label", "已识别" + intent.getLabel())
                        .put("intent", intent.getIntent())
                        .put("status", "done")
                        .toString());
                sendEvent(session, "chunk", "已为您跳转到学习路径规划。请使用 GET /api/learning-path?goal=xxx 获取学习路线。");
                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());
            }
            case IntentResult.RESOURCE_GENERATE -> {
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "orchestrator")
                        .put("label", "已识别" + intent.getLabel())
                        .put("intent", intent.getIntent())
                        .put("status", "done")
                        .toString());
                sendEvent(session, "chunk", "资源生成功能已就绪。请使用 POST /api/resources/generate 来生成讲义、题目或代码示例。");
                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());
            }
            case IntentResult.MEDIA_GENERATE -> {
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "orchestrator")
                        .put("label", "已识别" + intent.getLabel())
                        .put("intent", intent.getIntent())
                        .put("status", "done")
                        .toString());
                sendEvent(session, "chunk", "媒体生成功能已就绪。请使用 POST /api/resources/generate?type=ILLUSTRATION 生成示意图。");
                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());
            }
            case IntentResult.GREETING -> {
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "orchestrator")
                        .put("label", "已识别" + intent.getLabel())
                        .put("intent", intent.getIntent())
                        .put("status", "done")
                        .toString());
                sendEvent(session, "chunk", "你好！我是 LQRAgent 智能学习助手，可以帮你解答问题、规划学习路径、生成学习资源。请问有什么可以帮助你的？");
                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());
            }
            case IntentResult.HELP -> {
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "orchestrator")
                        .put("label", "已识别" + intent.getLabel())
                        .put("intent", intent.getIntent())
                        .put("status", "done")
                        .toString());
                sendEvent(session, "chunk", """
                        我可以帮你做这些事情：
                        1. 📖 解答问题 — 发送任何 Python 相关问题
                        2. 🗺️ 规划学习路径 — GET /api/learning-path?goal=xxx
                        3. 📝 生成学习资源 — POST /api/resources/generate
                        4. 🎨 生成示意图 — POST /api/resources/generate?type=ILLUSTRATION
                        5. ⚙️ 管理配置 — 管理员可通过 Admin 面板配置
                        
                        直接输入问题开始学习吧！""");
                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());
            }
            default -> {
                // qa_question + unknown → 走答疑通道
                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "qa_agent")
                        .put("label", "正在理解问题...")
                        .put("status", "running")
                        .toString());

                StringBuilder fullResponse = new StringBuilder();
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
                        persistAiMessage(userInfo.userId, finalSessionId, fullResponse.toString());
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
        }
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

    private record UserInfo(Long userId, String username) {}
}
