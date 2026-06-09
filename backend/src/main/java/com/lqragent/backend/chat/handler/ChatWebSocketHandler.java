package com.lqragent.backend.chat.handler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.agents.base.BaseAgent.AgentRequest;
import com.lqragent.backend.agents.base.BaseAgent.AgentResponse;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.serve.qa.QaAgent;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.orchestrator.OrchestratorCore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
    // Removed dead imports: LlmClient, GenerateSummaryTool, AdjustDifficultyTool,
    // DetectLearningStyleTool, LearningPathService, GetReviewScheduleTool,
    // AnalyzeWeaknessTool, GradeAnswerTool, GetInterventionTool, GetRecommendationTool, ProfileAgent

    private final ChatSessionService chatSessionService;
    private final QaAgent qaAgent;
    private final OrchestratorCore orchestratorCore;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSocketSessionManager sessionManager;
    private final LearnerProfileService learnerProfileService;
    private final AgentMemory agentMemory;
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
        log.info("[WS] received message: sessionId={}, payload={}", session.getId(), message.getPayload());
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

        if (!"message".equals(type) || content.isBlank()) {
            return;
        }

        // Resolve or create chat session
        Long sessionId = null;
        if (node.has("session_id") && !node.get("session_id").asText().isBlank()) {
            try {
                sessionId = Long.parseLong(node.get("session_id").asText());
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        String sessionIdStr = null;
        if (sessionId == null) {
            ChatSession chatSession = chatSessionService.createSession(userInfo.userId(), generateTitle(content));
            sessionId = chatSession.getId();
            sessionIdStr = String.valueOf(sessionId);
            sendEvent(session, "session_created", objectMapper.createObjectNode()
                    .put("session_id", sessionIdStr)
                    .put("title", chatSession.getTitle())
                    .toString());
        } else {
            // 验证会话是否存在，如果不存在则创建新会话并更新 sessionId
            ChatSession existingSession = chatSessionService.findById(sessionId).orElse(null);
            if (existingSession == null) {
                ChatSession newSession = chatSessionService.createSession(userInfo.userId(), generateTitle(content));
                sessionId = newSession.getId();
            }
            sessionIdStr = String.valueOf(sessionId);
        }

        // 记录用户消息到 Agent 记忆（同时持久化到DB）
        agentMemory.addUserMessage(userInfo.userId(), sessionId, content);

        final String finalSessionId = sessionIdStr;

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
            agentMemory.addAgentResponse(userInfo.userId(), Long.parseLong(finalSessionId), "抱歉，处理异常: " + e.getMessage(), "orchestrator");
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
            agentMemory.addAgentResponse(userInfo.userId(), Long.parseLong(finalSessionId), response, "orchestrator");
            triggerProfileExtractionAsync(userInfo.userId(), finalSessionId, session);
            return;
        }

        // QA → ReAct 智能体（通过 QaAgent 的 LLM 推理 + 工具调用循环）
        if ("qa".equals(route)) {
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
                    .put("label", "正在理解问题...")
                    .put("status", "running")
                    .toString());

            final Long currentUserId = userInfo.userId();
            try {
                // 加载对话历史
                List<AgentMemory.MemoryEntry> recentHistory = agentMemory.getRecentHistory(currentUserId, 10);
                List<Map<String, Object>> history = recentHistory.stream()
                        .map(entry -> {
                            Map<String, Object> msg = new java.util.LinkedHashMap<>();
                            msg.put("role", entry.getRole());
                            msg.put("content", entry.getContent());
                            return msg;
                        })
                        .toList();

                // 通过 QaAgent ReAct 循环处理（LLM 推理 + 工具调用）
                AgentRequest agentRequest = new AgentRequest("qa", content, Map.of());
                AgentResponse agentResponse = qaAgent.process(agentRequest, history);

                String qaResponse;
                if (agentResponse.success()) {
                    qaResponse = agentResponse.content();
                } else {
                    log.warn("[WS] QA agent failed: {}", agentResponse.error());
                    qaResponse = "抱歉，处理问题时出现异常。请稍后再试。";
                }

                sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "intelligent_qa")
                        .put("label", "回答完成")
                        .put("status", "done")
                        .toString());
                sendEvent(session, "chunk", qaResponse);
                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());
                agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionId), qaResponse, "qa_agent");
                triggerProfileExtractionAsync(currentUserId, finalSessionId, session);
            } catch (Exception e) {
                log.error("[WS] QA agent error: {}", e.getMessage(), e);
                String fallbackResponse = "抱歉，AI 服务暂时不可用。请稍后再试。";
                sendEvent(session, "chunk", fallbackResponse);
                sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", finalSessionId)
                        .toString());
                agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionId), fallbackResponse, "qa_agent");
            }
            return;
        }

        // Pipeline 完成 — OrchestratorCore 已通过 PipelineEngine 执行 Agent 并聚合结果
        if ("pipeline_complete".equals(route)) {
            String pipelineAgent = routeResult.containsKey("agent") ? (String) routeResult.get("agent") : "pipeline_engine";
            String pipelineResponse = routeResult.containsKey("response")
                    ? (String) routeResult.get("response")
                    : "任务执行完成。";
                
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", pipelineAgent)
                    .put("label", "处理完成")
                    .put("status", "done")
                    .toString());
            sendEvent(session, "chunk", pipelineResponse);
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", finalSessionId)
                    .toString());
            agentMemory.addAgentResponse(userInfo.userId(), Long.parseLong(finalSessionId), pipelineResponse, pipelineAgent);
            triggerProfileExtractionAsync(userInfo.userId(), finalSessionId, session);
            return;
        }
    
        // Pipeline 失败
        if ("pipeline_error".equals(route)) {
            String errorMsg = routeResult.containsKey("error")
                    ? (String) routeResult.get("error")
                    : "未知错误";
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "pipeline_engine")
                    .put("label", "处理失败")
                    .put("status", "failed")
                    .toString());
            sendEvent(session, "chunk", "抱歉，任务执行失败：" + errorMsg);
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", finalSessionId)
                    .toString());
            agentMemory.addAgentResponse(userInfo.userId(), Long.parseLong(finalSessionId),
                    "抱歉，任务执行失败：" + errorMsg, "pipeline_engine");
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
                .put("session_id", finalSessionId)
                .toString());
        agentMemory.addAgentResponse(userInfo.userId(), Long.parseLong(finalSessionId), fallback, "orchestrator");
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

    // ===== formatToolResponse removed — Pipeline handles formatting via Agent's ReAct loop =====

    private String generateTitle(String content) {
        String cleaned = content.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 30 ? cleaned.substring(0, 30) + "..." : cleaned;
    }
}
