package com.lqragent.backend.chat.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.intelligentqa.service.QaAgentService;
import com.lqragent.backend.agents.learn.path.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * /ws/chat WebSocket 端点处理器。
 * <p>
 * 职责：
 * - 管理前端 WebSocket 连接
 * - OrchestratorCore 意图识别 → 路由到对应智能体
 * - 流式响应转发给前端
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionService chatSessionService;
    private final QaAgentService qaAgentService;
    private final OrchestratorCore orchestratorCore;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSocketSessionManager sessionManager;
    private final LearnerProfileService learnerProfileService;
    private final LearningPathService learningPathService;
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

        RequestContext.init(userId);
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

        // 每条消息生成新 requestId
        RequestContext.init(userInfo.userId());

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

        // OrchestratorCore: 意图识别 + 路由
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "正在分析问题...")
                .put("status", "running")
                .toString());

        Map<String, Object> routeResult;
        try {
            routeResult = orchestratorCore.handleChatMessage(
                    String.valueOf(userInfo.userId()), content);
        } catch (Exception e) {
            log.error("[WS] orchestrator error: {}", e.getMessage(), e);
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "处理异常")
                    .put("status", "failed")
                    .toString());
            sendEvent(session, "error", "处理异常: " + e.getMessage());
            persistAiMessage(userInfo.userId(), finalSessionId, "抱歉，处理异常: " + e.getMessage());
            return;
        }

        String route = (String) routeResult.get("route");

        // 直接响应（问候、帮助）
        if ("direct".equals(route)) {
            String response = (String) routeResult.get("response");
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "处理完成")
                    .put("status", "done")
                    .toString());
            sendEvent(session, "chunk", response);
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", finalSessionId)
                    .toString());
            persistAiMessage(userInfo.userId(), finalSessionId, response);
            triggerProfileExtractionAsync(userInfo.userId(), finalSessionId, session);
            return;
        }

        // QA → 流式答疑通道
        if ("qa".equals(route)) {
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
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
                            .put("agent", "intelligent_qa")
                            .put("label", "回答完成")
                            .put("status", "done")
                            .toString());
                    sendEvent(session, "done", objectMapper.createObjectNode()
                            .put("session_id", finalSessionId)
                            .toString());
                    persistAiMessage(userInfo.userId(), finalSessionId, fullResponse.toString());
                    triggerProfileExtractionAsync(currentUserId, finalSessionId, session);
                }

                @Override
                public void onError(String error) {
                    sendEvent(session, "agent_step", objectMapper.createObjectNode()
                            .put("agent", "intelligent_qa")
                            .put("label", "处理失败")
                            .put("status", "failed")
                            .put("detail", error)
                            .toString());
                    sendEvent(session, "error", error);
                }

                @Override
                public void onSources(List<Map<String, Object>> sources) {
                    if (sources != null && !sources.isEmpty()) {
                        try {
                            var artifactNode = objectMapper.createObjectNode()
                                    .put("type", "artifact")
                                    .put("kind", "rag_sources");
                            var sourcesArray = objectMapper.valueToTree(sources);
                            artifactNode.set("payload", sourcesArray);
                            synchronized (session) {
                                session.sendMessage(new TextMessage(artifactNode.toString()));
                            }
                        } catch (IOException e) {
                            log.warn("[WS] rag_sources push failed", e);
                        }
                    }
                }
            });
            return;
        }

        // 其他路由（learning_path, resource）→ 直接调用服务
        String agent = routeResult.containsKey("agent") ? (String) routeResult.get("agent") : "orchestrator";
        
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", agent)
                .put("label", "正在处理...")
                .put("status", "running")
                .toString());
        
        String response;
        try {
            if ("learning_path".equals(route)) {
                // 直接调用学习路径服务
                var pathResult = learningPathService.generatePath(userInfo.userId(), content, null);
                StringBuilder sb = new StringBuilder();
                sb.append("好的，我为你生成了学习路径！\n\n");
                sb.append("目标：").append(pathResult.getGoal()).append("\n");
                sb.append("共 ").append(pathResult.getNodes().size()).append(" 个学习节点：\n\n");
                for (int i = 0; i < pathResult.getNodes().size(); i++) {
                    var pathNode = pathResult.getNodes().get(i);
                    sb.append(i + 1).append(". ").append(pathNode.getTitle()).append("\n");
                    if (pathNode.getDescription() != null && !pathNode.getDescription().isBlank()) {
                        sb.append("   ").append(pathNode.getDescription()).append("\n");
                    }
                }
                sb.append("\n你可以在「学习路径」页面查看详细内容和进度。");
                response = sb.toString();
            } else if ("resource".equals(route)) {
                response = "好的，我来帮你生成学习资源。请告诉我你想学习哪个知识点？";
            } else {
                response = "好的，我来帮你处理。";
            }
        } catch (Exception e) {
            log.error("[WS] service call failed: {}", e.getMessage(), e);
            response = "抱歉，处理时出现错误：" + e.getMessage();
        }
        
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", agent)
                .put("label", "处理完成")
                .put("status", "done")
                .toString());
        sendEvent(session, "chunk", response);
        sendEvent(session, "done", objectMapper.createObjectNode()
                .put("session_id", finalSessionId)
                .toString());
        persistAiMessage(userInfo.userId(), finalSessionId, response);
        triggerProfileExtractionAsync(userInfo.userId(), finalSessionId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RequestContext.clear();
        sessionManager.unregister(session.getId());
        log.info("[WS] client disconnected: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        RequestContext.clear();
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

    private void triggerProfileExtractionAsync(Long userId, String chatSessionId, WebSocketSession ws) {
        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "正在从对话更新学习画像…")
                        .put("status", "running")
                        .toString());
                learnerProfileService.extractFromSession(userId, chatSessionId);
                sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "学习画像已更新")
                        .put("status", "done")
                        .toString());
            } catch (Exception e) {
                log.warn("[WS] 画像抽取失败: userId={}, sessionId={}", userId, chatSessionId, e);
                sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "画像更新已跳过")
                        .put("status", "done")
                        .toString());
            }
        });
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
}
