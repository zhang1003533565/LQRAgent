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
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.mediageneration.service.MediaGenerationService;
import com.lqragent.backend.agents.mediageneration.service.PromptGenerationService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
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
import com.lqragent.backend.orchestrator.pipeline.StepResult;
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
    private final LlmClient llmClient;
    private final MediaGenerationService mediaGenerationService;
    private final PromptGenerationService promptGenerationService;
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
            // 立即告知前端正在处理
            sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "orchestrator")
                    .put("label", "正在分析...")
                    .put("status", "running")
                    .toString());
            sendEvent(session, "chunk", "正在处理，请稍候...");

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
                                        // 只发送成功步骤通知（失败步骤静默，避免前端显示一堆红线）
                                        if (!success) {
                                            log.debug("[WS] step {} ({}) failed, skipped in UI", stepId, agentId);
                                            return;
                                        }
                                        sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                                .put("agent", agentId)
                                                .put("label", "已完成")
                                                .put("status", "done")
                                                .toString());

                                        // 先发送 artifact（学习路径、图表等）
                                        if (stepData != null) {
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

                    // Pipeline 完成后，检查是否成功
                    String agent = plan.pipelineConfig().getPipelineId();

                    if (pipelineResult.isSuccess()) {
                        // 聚合所有步骤内容用于记忆记录
                        StringBuilder allContent = new StringBuilder();
                        List<StepResult> stepResults = pipelineResult.getStepResults();
                        if (stepResults != null) {
                            for (var sr : stepResults) {
                                if (sr.isSuccess() && sr.getData() != null) {
                                    String stepContent = extractStepContent(sr.getAgentId(), sr.getData());
                                    if (stepContent != null && !stepContent.isBlank()) {
                                        if (allContent.length() > 0) allContent.append("\n\n");
                                        allContent.append(stepContent);
                                    }
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
                    } else {
                        // Pipeline 执行失败 → 回退处理
                        String pipelineId = plan.pipelineConfig().getPipelineId();
                        String errorMsg = pipelineResult.getErrorMessage() != null
                                ? pipelineResult.getErrorMessage() : "未知错误";
                        log.warn("[WS] pipeline {} failed ({}), using fallback", pipelineId, errorMsg);

                        // 如果是学习路径 pipeline，尝试直接生成
                        if ("learning_path".equals(pipelineId) || "learning_path_core".equals(pipelineId)) {
                            try {
                                LearningPathDto pathDto = learningPathService.generatePath(
                                        currentUserId, content, null);
                                String planText = pathDto.getPlanDescription() != null
                                        ? pathDto.getPlanDescription() : "";
                                var payloadNode = objectMapper.createObjectNode()
                                        .put("goal", pathDto.getGoal() != null ? pathDto.getGoal() : content)
                                        .put("planDescription", planText);
                                payloadNode.set("nodes", objectMapper.valueToTree(pathDto.getNodes()));
                                sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                        .put("agent", "learning_path")
                                        .put("label", "学习路径已生成")
                                        .put("status", "done")
                                        .toString());
                                sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                                        .put("kind", "learning_path")
                                        .set("payload", payloadNode)
                                        .toString());
                                sendEvent(wsSession, "chunk", planText);
                                sendEvent(wsSession, "done", objectMapper.createObjectNode()
                                        .put("session_id", finalSessionIdForPipeline)
                                        .toString());
                                agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline),
                                        planText, "learning_path_fallback");
                                triggerProfileExtractionAsync(currentUserId, finalSessionIdForPipeline, wsSession);
                                return;
                            } catch (Exception e2) {
                                log.warn("[WS] learning path fallback also failed: {}", e2.getMessage());
                            }
                        }

                        // 图表生成 pipeline → 调用 MediaGenerationService 生成图片 + Mermaid 代码
                        if ("diagram".equals(pipelineId)) {
                            try {
                                // 生成图片
                                String provider = runtimeConfig.get("image.binding", "mock");
                                String apiKey = runtimeConfig.get("image.api-key", "");
                                boolean keySet = !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder");
                                Map<String, Object> promptResult = promptGenerationService.generatePrompt(content, "image");
                                String imagePrompt = (String) promptResult.getOrDefault("prompt", content);
                                String imageUrl = mediaGenerationService.generateImageByPrompt(imagePrompt);
                                boolean isMock = imageUrl.startsWith("data:");

                                // 发图片 artifact
                                var imgPayload = objectMapper.createObjectNode()
                                        .put("url", imageUrl)
                                        .put("prompt", imagePrompt)
                                        .put("mediaType", "image");
                                sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                        .put("agent", "diagram")
                                        .put("label", "示意图已生成")
                                        .put("status", "done")
                                        .toString());
                                sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                                        .put("kind", "media_image")
                                        .set("payload", imgPayload)
                                        .toString());
                                String mode = isMock ? "🧩 占位图（provider=" + provider + ", key=" + (keySet ? "已配置" : "未配置") + "）" : "🖼️ 真实图片";
                                String msg = "🎨 已生成示意图 —— " + mode + "\n\n"
                                        + "提示词：" + imagePrompt + "\n\n"
                                        + (isMock && !keySet ? "⚠️ 未配置 image.api-key，当前为占位模式（内置 SVG）。\n" : "")
                                        + (isMock && keySet ? "⚠️ 已配置 API Key 但 API 调用未返回真实图片，请检查 provider=" + provider + " 是否与 key 匹配\n" : "");
                                sendEvent(wsSession, "chunk", msg);
                                sendEvent(wsSession, "done", objectMapper.createObjectNode()
                                        .put("session_id", finalSessionIdForPipeline)
                                        .toString());
                                agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline),
                                        msg, "diagram_fallback_image");
                                triggerProfileExtractionAsync(currentUserId, finalSessionIdForPipeline, wsSession);
                                return;
                            } catch (Exception e2) {
                                log.warn("[WS] diagram image fallback also failed: {}", e2.getMessage());
                                // 如果图片生成也失败，继续兜底到 LLM 文本回答
                            }
                        }

                        // 资源生成 pipeline → 使用 LLM 生成
                        if ("resource".equals(pipelineId)) {
                            try {
                                String resp = llmClient.chat(
                                    "你是一个学习资源生成专家。根据用户的需求生成结构化的学习资源，包含：讲义、代码示例（如需）、练习题等。",
                                    java.util.List.of(java.util.Map.of("role", "user", "content", content)),
                                    null
                                ).content();
                                sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                        .put("agent", "resource")
                                        .put("label", "资源已生成")
                                        .put("status", "done")
                                        .toString());
                                sendEvent(wsSession, "chunk", resp);
                                sendEvent(wsSession, "done", objectMapper.createObjectNode()
                                        .put("session_id", finalSessionIdForPipeline)
                                        .toString());
                                agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline),
                                        resp, "resource_fallback");
                                triggerProfileExtractionAsync(currentUserId, finalSessionIdForPipeline, wsSession);
                                return;
                            } catch (Exception e2) {
                                log.warn("[WS] resource fallback also failed: {}", e2.getMessage());
                            }
                        }

                        // 媒体生成 pipeline → 调用 MediaGenerationService 生成图片
                        if ("media_gen".equals(pipelineId)) {
                            try {
                                // 0. 读取当前配置状态方便诊断
                                String provider = runtimeConfig.get("image.binding", "mock");
                                String apiKey = runtimeConfig.get("image.api-key", "");
                                boolean keySet = !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder");
                                // 1. 用 LLM 生成 prompt
                                Map<String, Object> promptResult = promptGenerationService.generatePrompt(content, "image");
                                String imagePrompt = (String) promptResult.getOrDefault("prompt", content);
                                // 2. 生成图片
                                String imageUrl = mediaGenerationService.generateImageByPrompt(imagePrompt);
                                // 3. 发 artifact
                                boolean isMock = imageUrl.startsWith("data:");
                                var payload = objectMapper.createObjectNode()
                                        .put("url", imageUrl)
                                        .put("prompt", imagePrompt)
                                        .put("mediaType", "image");
                                sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                        .put("agent", "media_gen")
                                        .put("label", "示意图已生成")
                                        .put("status", "done")
                                        .toString());
                                sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                                        .put("kind", "media_image")
                                        .set("payload", payload)
                                        .toString());
                                String mode = isMock ? "🧩 占位图（provider=" + provider + ", key=" + (keySet ? "已配置" : "未配置") + "）" : "🖼️ 真实图片";
                                String msg = "🎨 已生成示意图 —— " + mode + "\n\n"
                                        + "提示词：" + imagePrompt + "\n\n"
                                        + (isMock && !keySet ? "⚠️ 未配置 image.api-key，当前为占位模式（内置 SVG）。\n" : "")
                                        + (isMock && keySet ? "⚠️ 已配置 API Key 但 API 调用未返回真实图片，请检查 provider=" + provider + " 是否与 key 匹配\n" : "");
                                sendEvent(wsSession, "chunk", msg);
                                sendEvent(wsSession, "done", objectMapper.createObjectNode()
                                        .put("session_id", finalSessionIdForPipeline)
                                        .toString());
                                agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline),
                                        msg, "media_fallback");
                                triggerProfileExtractionAsync(currentUserId, finalSessionIdForPipeline, wsSession);
                                return;
                            } catch (Exception e2) {
                                String errMsg = "❌ 媒体生成失败: " + e2.getMessage();
                                log.warn("[WS] media fallback also failed: {}", e2.getMessage());
                                sendEvent(wsSession, "chunk", errMsg);
                                sendEvent(wsSession, "done", objectMapper.createObjectNode()
                                        .put("session_id", finalSessionIdForPipeline)
                                        .toString());
                            }
                        }

                        // 兜底：回退到 QA

                        String fallbackResp = null;
                        try {
                            AgentMessage agentRequest = AgentMessage.request("qa", "ws_handler", "qa_agent",
                                    Map.of("goal", content));
                            // 加载最近对话历史传给 QA agent 保持上下文
                            List<AgentMemory.MemoryEntry> recentHistoryLocal = agentMemory.getRecentHistory(currentUserId, 10);
                            List<Map<String, Object>> qaHistory = new java.util.ArrayList<>();
                            for (var e : recentHistoryLocal) {
                                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                                m.put("role", e.getRole());
                                m.put("content", e.getContent());
                                qaHistory.add(m);
                            }
                            AgentMessage agentResponse = qaAgent.processWithHistory(agentRequest, qaHistory);
                            if (agentResponse.getPerformative() == Performative.INFORM) {
                                Object respContent = agentResponse.getContent().get("content");
                                fallbackResp = respContent != null ? respContent.toString() : "";
                            }
                        } catch (Exception e2) {
                            log.error("[WS] QA fallback also failed: {}", e2.getMessage());
                            fallbackResp = null;
                        }

                        if (fallbackResp != null && !fallbackResp.isBlank()) {
                            sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                    .put("agent", "intelligent_qa")
                                    .put("label", "回答完成")
                                    .put("status", "done")
                                    .toString());
                            sendEvent(wsSession, "chunk", fallbackResp);
                            sendEvent(wsSession, "done", objectMapper.createObjectNode()
                                    .put("session_id", finalSessionIdForPipeline)
                                    .toString());
                            agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline),
                                    fallbackResp, "qa_fallback");
                            triggerProfileExtractionAsync(currentUserId, finalSessionIdForPipeline, wsSession);
                        } else {
                            sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                                    .put("agent", agent)
                                    .put("label", "执行失败")
                                    .put("status", "failed")
                                    .toString());
                            sendEvent(wsSession, "chunk", "抱歉，任务执行失败：" + errorMsg);
                            sendEvent(wsSession, "done", objectMapper.createObjectNode()
                                    .put("session_id", finalSessionIdForPipeline)
                                    .toString());
                            agentMemory.addAgentResponse(currentUserId, Long.parseLong(finalSessionIdForPipeline),
                                    "抱歉，任务执行失败：" + errorMsg, agent);
                        }
                    }

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
                    List<Map<String, Object>> qaHistory = new java.util.ArrayList<>();
                    for (var e : recentHistory) {
                        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                        m.put("role", e.getRole());
                        m.put("content", e.getContent());
                        qaHistory.add(m);
                    }
                    AgentMessage agentRequest = AgentMessage.request("qa", "ws_handler", "qa_agent",
                            Map.of("goal", content));
                    AgentMessage agentResponse = qaAgent.processWithHistory(agentRequest, qaHistory);

                    if (agentResponse.getPerformative() == Performative.INFORM) {
                        Object respContent = agentResponse.getContent().get("content");
                        qaResponse = respContent != null ? respContent.toString() : "";
                    } else {
                        log.warn("[WS] QA agent failed: {}", agentResponse.getContent().get("error"));
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
