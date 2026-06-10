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
import com.lqragent.backend.agents.learn.path.dto.LearningPathDto;
import com.lqragent.backend.agents.learn.path.service.LearningPathService;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.serve.qa.QaAgent;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

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
    private final PipelineEngine pipelineEngine;
    private final AiServerWsProxy aiServerWsProxy;
    private final AppRuntimeConfig runtimeConfig;
    private final ChatMessageRepository chatMessageRepository;
    private final WebSocketSessionManager sessionManager;
    private final LearnerProfileService learnerProfileService;
    private final AgentMemory agentMemory;
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

        // 把 chatSessionId 存入 session attributes（供 AiServerWsProxy 回调使用）
        session.getAttributes().put("chatSessionId", sessionId);

        // ==================== 新路径：ai-server Agentic Pipeline ====================
        if (runtimeConfig.isUseAgenticPipeline()) {
            // 获取对话历史（传给 ai-server 作为上下文）
            String chatHistory = agentMemory.getFormattedHistory(userInfo.userId(), 10);

            // 获取用户画像摘要
            String learnerProfile = "";
            try {
                learnerProfile = learnerProfileService.getProfileSummary(userInfo.userId());
            } catch (Exception e) {
                log.warn("[WS] getProfileSummary failed: {}", e.getMessage());
            }

            // 交给 AiServerWsProxy 处理（它负责：发消息给 ai-server + 翻译事件 + 存库）
            aiServerWsProxy.startSession(
                    session.getId(),
                    userInfo.userId(),
                    sessionId,
                    content,
                    chatHistory,
                    learnerProfile,
                    session
            );
            return;
        }
        // ==================== 旧路径：Java 自有的 Agent 系统（回滚用） ====================

        // 记录用户消息到 Agent 记忆（同时持久化到DB）
        agentMemory.addUserMessage(userInfo.userId(), sessionId, content);

        final String finalSessionId = sessionIdStr;

        // 获取对话历史（用于 PlanningAgent 上下文理解）
        String chatHistory = agentMemory.getFormattedHistory(userInfo.userId(), 10);

        // OrchestratorCore: 仅做意图识别（快速，约2秒）
        sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "正在分析问题...")
                .put("status", "running")
                .toString());

        PlanResult plan;
        try {
            plan = orchestratorCore.planOnly(
                    String.valueOf(userInfo.userId()), content, chatHistory);
        } catch (Exception e) {
            log.error("[WS] planOnly error: {}", e.getMessage(), e);
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "处理异常")
                    .put("status", "failed")
                    .toString());
            sendEvent(session, "error", "处理异常: " + e.getMessage());
            agentMemory.addAgentResponse(userInfo.userId(), Long.parseLong(finalSessionId), "抱歉，处理异常: " + e.getMessage(), "orchestrator");
            return;
        }

        // CLARIFY 类型 → 发送引导性问题，等待用户确认
        if (plan.isClarify()) {
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "需要更多信息")
                    .put("status", "done")
                    .toString());

            // 构建引导性问题
            StringBuilder clarifyMsg = new StringBuilder();
            List<String> questions = plan.clarifyQuestions();
            if (questions != null && !questions.isEmpty()) {
                for (int i = 0; i < questions.size(); i++) {
                    clarifyMsg.append(i + 1).append(". ").append(questions.get(i)).append("\n");
                }
            }
            clarifyMsg.append("\n请告诉我这些信息，我会为你量身定制学习路径。");

            sendEvent(session, "chunk", clarifyMsg.toString());
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", finalSessionId)
                    .toString());
            agentMemory.addAgentResponse(userInfo.userId(), Long.parseLong(finalSessionId), clarifyMsg.toString(), "orchestrator");
            return;
        }

        // Pipeline 类型 → 异步执行，立即返回进度
        if (plan.isPipeline() && plan.pipelineConfig() != null) {
            // 立即告知前端正在规划
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "正在规划学习路径...")
                    .put("status", "running")
                    .toString());
            sendEvent(session, "chunk", "正在为你规划学习路径，请稍候...");

            final Long currentUserId = userInfo.userId();
            final String finalSessionIdForPipeline = finalSessionId;
            final WebSocketSession wsSession = session;

            // 在新线程中异步执行 Pipeline
            CompletableFuture.runAsync(() -> {
                try {
                    // 调用 handlePipelineAsync，传入 callback 处理步骤通知
                    PipelineResult pipelineResult = orchestratorCore.handlePipelineAsync(
                            plan, String.valueOf(currentUserId), content,
                            new PipelineEngine.StepCallback() {
                                @Override
                                public void onStepComplete(String stepId, String agentId, boolean success, Map<String, Object> stepData) {
                                    try {
                                        // 发送步骤完成通知
                                        sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                                .put("agent", agentId)
                                                .put("label", success ? "已完成" : "执行失败")
                                                .put("status", success ? "done" : "failed")
                                                .toString());

                                        // 先发送 artifact（学习路径、图表等）
                                        if (success && stepData != null) {
                                            // 学习路径特殊处理：发送 artifact
                                            if (agentId.contains("learning_path")) {
                                                try {
                                                    java.util.Optional<LearningPathDto> pathOpt = learningPathService.getCurrentPath(currentUserId);
                                                    if (pathOpt.isPresent()) {
                                                        LearningPathDto pathDto = pathOpt.get();
                                                        var payloadNode = objectMapper.createObjectNode()
                                                                .put("goal", pathDto.getGoal())
                                                                .put("planDescription", pathDto.getPlanDescription() != null ? pathDto.getPlanDescription() : "");
                                                        payloadNode.set("nodes", objectMapper.valueToTree(pathDto.getNodes()));
                                                        sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                                                                .put("kind", "learning_path")
                                                                .set("payload", payloadNode)
                                                                .toString());
                                                    }
                                                } catch (Exception e) {
                                                    log.warn("[WS] async: failed to fetch learning path: {}", e.getMessage());
                                                }
                                            }

                                            // 图表特殊处理：发送 diagram artifact
                                            if (agentId.contains("diagram")) {
                                                Object contentObj = stepData.get("content");
                                                if (contentObj != null) {
                                                    String diagramCode = String.valueOf(contentObj);
                                                    String format = "mermaid";
                                                    int start = diagramCode.indexOf("```mermaid");
                                                    if (start >= 0) {
                                                        start = diagramCode.indexOf('\n', start) + 1;
                                                        int end = diagramCode.indexOf("```", start);
                                                        if (end > start) {
                                                            diagramCode = diagramCode.substring(start, end).trim();
                                                        }
                                                    }
                                                    var diagPayload = objectMapper.createObjectNode()
                                                            .put("diagram", diagramCode)
                                                            .put("format", format);
                                                    sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                                                            .put("kind", "diagram")
                                                            .set("payload", diagPayload)
                                                            .toString());
                                                }
                                            }
                                        }

                                        // 再发送步骤文本内容
                                        // 注意：profile_agent 是内部处理，不输出给用户
                                        if (success && stepData != null && !agentId.contains("profile")) {
                                            String stepContent = extractStepContent(agentId, stepData);
                                            if (stepContent != null && !stepContent.isBlank()) {
                                                sendEvent(wsSession, "chunk", stepContent);
                                            }
                                        }

                                    } catch (Exception e) {
                                        log.error("[WS] step callback error: {}", e.getMessage(), e);
                                    }
                                }
                            });

                    // Pipeline 完成后，只发送 done 事件（内容已在 callback 中分步推送）
                    String agent = plan.pipelineConfig().getPipelineId();

                    // 聚合所有步骤内容用于记忆记录
                    StringBuilder allContent = new StringBuilder();
                    for (var sr : pipelineResult.getStepResults()) {
                        if (sr.isSuccess() && sr.getData() != null) {
                            String stepContent = extractStepContent(sr.getAgentId(), sr.getData());
                            if (stepContent != null && !stepContent.isBlank()) {
                                if (allContent.length() > 0) allContent.append("\n\n");
                                allContent.append(stepContent);
                            }
                        }
                    }
                    String finalContent = allContent.isEmpty() ? "任务执行完成。" : allContent.toString();

                    sendEvent(wsSession, "done", objectMapper.createObjectNode()
                            .put("session_id", finalSessionIdForPipeline)
                            .toString());

                    // 记录到记忆
                    agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline), finalContent, agent);

                    // 记忆更新放到输出后异步执行
                    triggerProfileExtractionAsync(currentUserId, finalSessionIdForPipeline, wsSession);

                } catch (Exception e) {
                    log.error("[WS] async pipeline error: {}", e.getMessage(), e);
                    sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                            .put("agent", "pipeline_engine")
                            .put("label", "处理失败")
                            .put("status", "failed")
                            .toString());
                    sendEvent(wsSession, "chunk", "抱歉，任务执行失败：" + e.getMessage());
                    sendEvent(wsSession, "done", objectMapper.createObjectNode()
                            .put("session_id", finalSessionIdForPipeline)
                            .toString());

                    agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline),
                            "抱歉，任务执行失败：" + e.getMessage(), "pipeline_engine");
                }
            });

            // 立即返回，不阻塞 WebSocket 线程
            return;
        }

        // 非 Pipeline 类型 → 原有同步处理
        Map<String, Object> routeResult;
        try {
            routeResult = orchestratorCore.handleSimpleRequest(
                    plan.intent(), content);
        } catch (Exception e) {
            log.error("[WS] handleSimpleRequest error: {}", e.getMessage(), e);
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

        // QA → PipelineEngine 智能问答流水线（含并行知识检索+内容分析），失败时回退到直接 QaAgent 调用
        if ("qa".equals(route)) {
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
                    .put("label", "正在理解问题...")
                    .put("status", "running")
                    .toString());

            final Long currentUserId = userInfo.userId();
            String qaResponse = null;
            boolean pipelineSucceeded = false;

            // 优先尝试 PipelineEngine
            try {
                PipelineConfig qaConfig = PipelineTemplates.questionAnswer();
                TaskContext context = new TaskContext("qa-" + System.currentTimeMillis(),
                        String.valueOf(currentUserId), finalSessionId, content);
                PipelineResult pipelineResult = pipelineEngine.execute(qaConfig, context);

                if (pipelineResult.isSuccess()) {
                    qaResponse = orchestratorCore.aggregateResults(qaConfig, pipelineResult);
                    pipelineSucceeded = true;
                } else {
                    log.warn("[WS] QA pipeline failed: {}, falling back",
                            pipelineResult.getErrorMessage());
                }
            } catch (Exception e) {
                log.warn("[WS] QA pipeline exception: {}, falling back", e.getMessage());
            }

            // 回退：直接调用 QaAgent
            if (!pipelineSucceeded) {
                try {
                    List<AgentMemory.MemoryEntry> recentHistory = agentMemory.getRecentHistory(currentUserId, 10);
                    List<Map<String, Object>> history = recentHistory.stream()
                            .map(entry -> {
                                Map<String, Object> msg = new java.util.LinkedHashMap<>();
                                msg.put("role", entry.getRole());
                                msg.put("content", entry.getContent());
                                return msg;
                            })
                            .toList();

                    AgentRequest agentRequest = new AgentRequest("qa", content, Map.of());
                    AgentResponse agentResponse = qaAgent.process(agentRequest, history);

                    if (agentResponse.success()) {
                        qaResponse = agentResponse.content();
                    } else {
                        log.warn("[WS] QA agent failed: {}", agentResponse.error());
                        qaResponse = "抱歉，处理问题时出现异常。请稍后再试。";
                    }
                } catch (Exception e) {
                    log.error("[WS] QA fallback error: {}", e.getMessage(), e);
                    qaResponse = "抱歉，AI 服务暂时不可用。请稍后再试。";
                }
            }

            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
                    .put("label", pipelineSucceeded ? "智能流水线完成" : "回答完成")
                    .put("status", "done")
                    .toString());
            sendEvent(session, "chunk", qaResponse);
            sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", finalSessionId)
                    .toString());
            agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionId), qaResponse,
                    pipelineSucceeded ? "pipeline_qa" : "qa_agent");
            triggerProfileExtractionAsync(currentUserId, finalSessionId, session);
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

    /**
     * 从步骤数据中提取内容文本
     */
    private String extractStepContent(String agentId, Map<String, Object> data) {
        // 优先取 content 字段（Agent 的 LLM 回答）
        if (data.containsKey("content")) {
            Object content = data.get("content");
            if (content != null && !String.valueOf(content).isBlank()) {
                return String.valueOf(content);
            }
        }
        // summary/result 字段
        if (data.containsKey("summary")) {
            return String.valueOf(data.get("summary"));
        }
        if (data.containsKey("result")) {
            return String.valueOf(data.get("result"));
        }
        if (data.containsKey("llm_analysis")) {
            return String.valueOf(data.get("llm_analysis"));
        }
        // 学习路径特殊处理
        if (agentId.contains("learning_path") && data.containsKey("nodes")) {
            return "学习路径已生成，包含 " + data.get("nodeCount") + " 个节点。";
        }
        return null;
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
