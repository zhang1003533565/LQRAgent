package com.lqragent.backend.chat.handler;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.orchestrator.planning.PlanIntent;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * /ws/chat WebSocket 端点处理器（精简版）
 * <p>
 * 只负责：连接管理 + 消息解析 + 路由分发
 * 具体处理逻辑委托给 ChatRouteDispatcher
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ChatSessionService chatSessionService;
    private final ChatRouteDispatcher dispatcher;
    private final AiServerWsProxy aiServerWsProxy;
    private final AppRuntimeConfig runtimeConfig;
    private final WebSocketSessionManager sessionManager;
    private final AgentMemory agentMemory;
    private final LearnerProfileService learnerProfileService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // WsSender 实现：委托给本类的 sendEvent 方法
    private final WsSender wsSender = this::sendEvent;

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

        RequestContext.init(userInfo.userId());

        String type = node.has("type") ? node.get("type").asText() : "";
        String content = node.has("content") ? node.get("content").asText() : "";

        if (!"message".equals(type) || content.isBlank()) {
            return;
        }

        // 解析或创建聊天会话
        Long chatSessionId = resolveChatSession(node, userInfo.userId(), content, session);
        if (chatSessionId == null) return;

        session.getAttributes().put("chatSessionId", chatSessionId);

        // ===== 新路径：ai-server Agentic Pipeline =====
        if (runtimeConfig.isUseAgenticPipeline()) {
            String chatHistory = agentMemory.getFormattedHistory(userInfo.userId(), 10);
            String learnerProfile = "";
            try {
                learnerProfile = learnerProfileService.getProfileSummary(userInfo.userId());
            } catch (Exception e) {
                log.warn("[WS] getProfileSummary failed: {}", e.getMessage());
            }
            aiServerWsProxy.startSession(session.getId(), userInfo.userId(),
                    chatSessionId, content, chatHistory, learnerProfile, session);
            return;
        }

        // ===== 旧路径：Java Agent 系统 =====
        agentMemory.addUserMessage(userInfo.userId(), chatSessionId, content);
        String sessionIdStr = String.valueOf(chatSessionId);
        String chatHistory = agentMemory.getFormattedHistory(userInfo.userId(), 10);

        // 意图识别
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "正在分析问题...")
                .put("status", "running")
                .toString());

        PlanResult plan;
        try {
            plan = dispatcher.planOnly(userInfo.userId(), content, chatHistory);
        } catch (Exception e) {
            log.error("[WS] planOnly error: {}", e.getMessage(), e);
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "处理异常")
                    .put("status", "failed")
                    .toString());
            sendEvent(session, "error", "处理异常: " + e.getMessage());
            agentMemory.addAgentResponse(userInfo.userId(), chatSessionId,
                    "抱歉，处理异常: " + e.getMessage(), "orchestrator");
            return;
        }

        // 路由分发
        if (plan.isClarify()) {
            dispatcher.handleClarify(session, plan, userInfo.userId(), sessionIdStr, wsSender);
        } else if (plan.isPipeline() && plan.pipelineConfig() != null) {
            dispatcher.handlePipeline(session, plan, userInfo.userId(), sessionIdStr, content, wsSender);
        } else {
            handleSimpleRoute(session, plan, userInfo.userId(), sessionIdStr, content, chatHistory);
        }
    }

    /**
     * 简单路由处理（问候/帮助/QA）
     */
    private void handleSimpleRoute(WebSocketSession session, PlanResult plan,
                                   Long userId, String sessionId, String content, String chatHistory) {
        Map<String, Object> routeResult;
        try {
            routeResult = dispatcher.handleSimpleRequest(plan.intent(), content);
        } catch (Exception e) {
            log.error("[WS] handleSimpleRequest error: {}", e.getMessage(), e);
            sendEvent(session, "error", "处理异常: " + e.getMessage());
            agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                    "抱歉，处理异常: " + e.getMessage(), "orchestrator");
            return;
        }

        String route = (String) routeResult.get("route");

        if ("direct".equals(route)) {
            String response = (String) routeResult.get("response");
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "处理完成")
                    .put("status", "done")
                    .toString());
            sendEvent(session, "chunk", response);
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .toString());
            agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), response, "orchestrator");
            dispatcher.triggerProfileExtractionAsync(userId, sessionId, session, wsSender);
            return;
        }

        if ("qa".equals(route)) {
            dispatcher.handleQa(session, userId, sessionId, content, chatHistory, wsSender);
            return;
        }

        // 兜底
        String fallback = "我可以帮你：\n" +
                "1. 解答问题 - 直接提问即可\n" +
                "2. 规划学习路径 - 说「帮我制定学习计划」\n" +
                "3. 生成学习资源 - 说「帮我生成讲义/练习题」\n" +
                "4. 分析学习状态 - 说「我哪些知识点薄弱」\n\n" +
                "请问有什么可以帮助你的？";
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "处理完成")
                .put("status", "done")
                .toString());
        sendEvent(session, "chunk", fallback);
        sendEvent(session, "done", objectMapper.createObjectNode()
                .put("session_id", sessionId)
                .toString());
        agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), fallback, "orchestrator");
        dispatcher.triggerProfileExtractionAsync(userId, sessionId, session, wsSender);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        RequestContext.clear();
        aiServerWsProxy.onFrontendDisconnected(session.getId());
        sessionManager.unregister(session.getId());
        log.info("[WS] client disconnected: sessionId={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        RequestContext.clear();
        aiServerWsProxy.onFrontendDisconnected(session.getId());
        log.error("[WS] transport error: sessionId={}, msg={}", session.getId(), exception.getMessage());
        sessionManager.unregister(session.getId());
    }

    // ==================== 辅助方法 ====================

    private Long resolveChatSession(JsonNode node, Long userId, String content, WebSocketSession session) {
        Long sessionId = null;
        if (node.has("session_id") && !node.get("session_id").asText().isBlank()) {
            try {
                sessionId = Long.parseLong(node.get("session_id").asText());
            } catch (NumberFormatException ignored) {}
        }

        if (sessionId == null) {
            ChatSession chatSession = chatSessionService.createSession(userId, generateTitle(content));
            sessionId = chatSession.getId();
            sendEvent(session, "session_created", objectMapper.createObjectNode()
                    .put("session_id", String.valueOf(sessionId))
                    .put("title", chatSession.getTitle())
                    .toString());
        } else {
            ChatSession existingSession = chatSessionService.findById(sessionId).orElse(null);
            if (existingSession == null) {
                ChatSession newSession = chatSessionService.createSession(userId, generateTitle(content));
                sessionId = newSession.getId();
            }
        }
        return sessionId;
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

    private String generateTitle(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 30 ? cleaned.substring(0, 30) + "..." : cleaned;
    }
}
