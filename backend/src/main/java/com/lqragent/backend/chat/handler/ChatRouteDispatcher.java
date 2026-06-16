package com.lqragent.backend.chat.handler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.agents.mediageneration.service.MediaGenerationService;
import com.lqragent.backend.agents.mediageneration.service.PromptGenerationService;
import com.lqragent.backend.agents.qa.QaAgent;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.planning.PlanIntent;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天路由分发器 — 负责意图识别后的路由处理逻辑。
 * <p>
 * 从 ChatWebSocketHandler 中拆分出来，职责：
 * - CLARIFY → 发送引导性问题
 * - Pipeline → 异步执行流水线 + 步骤回调
 * - SIMPLE/direct → 直接响应
 * - QA → PipelineEngine 或回退 QaAgent
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRouteDispatcher {

    private final OrchestratorCore orchestratorCore;
    private final PipelineEngine pipelineEngine;
    private final QaAgent qaAgent;
    private final AgentMemory agentMemory;
    private final LearnerProfileService learnerProfileService;
    private final LlmClient llmClient;
    private final MediaGenerationService mediaGenerationService;
    private final PromptGenerationService promptGenerationService;
    private final LearningPathService learningPathService;
    private final AppRuntimeConfig runtimeConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== CLARIFY ====================

    public void handleClarify(WebSocketSession session, PlanResult plan,
                              Long userId, String sessionId, WsSender sender) {
        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "需要更多信息")
                .put("status", "done")
                .toString());

        StringBuilder clarifyMsg = new StringBuilder();
        List<String> questions = plan.clarifyQuestions();
        if (questions != null && !questions.isEmpty()) {
            for (int i = 0; i < questions.size(); i++) {
                clarifyMsg.append(i + 1).append(". ").append(questions.get(i)).append("\n");
            }
        }
        clarifyMsg.append("\n请告诉我这些信息，我会为你量身定制学习路径。");

        sender.sendEvent(session, "chunk", clarifyMsg.toString());
        sender.sendEvent(session, "done", objectMapper.createObjectNode()
                .put("session_id", sessionId)
                .toString());
        agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), clarifyMsg.toString(), "orchestrator");
    }

    // ==================== Pipeline ====================

    public void handlePipeline(WebSocketSession session, PlanResult plan,
                               Long userId, String sessionId, String content, WsSender sender) {
        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "正在分析...")
                .put("status", "running")
                .toString());

        CompletableFuture.runAsync(() -> {
            try {
                PipelineResult pipelineResult = orchestratorCore.handlePipelineAsync(
                        plan, String.valueOf(userId), content,
                        buildStepCallback(session, userId, sessionId, sender));

                String agent = plan.pipelineConfig().getPipelineId();

                if (pipelineResult.isSuccess()) {
                    handlePipelineSuccess(session, pipelineResult, userId, sessionId, agent, sender);
                } else {
                    handlePipelineFailure(session, pipelineResult, userId, sessionId, content, agent, sender);
                }
            } catch (Exception e) {
                log.error("[WS] async pipeline error: {}", e.getMessage(), e);
                sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "pipeline_engine")
                        .put("label", "处理失败")
                        .put("status", "failed")
                        .toString());
                sender.sendEvent(session, "chunk", "抱歉，任务执行失败：" + e.getMessage());
                sender.sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", sessionId)
                        .toString());
                agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                        "抱歉，任务执行失败：" + e.getMessage(), "pipeline_engine");
            }
        });
    }

    private PipelineEngine.StepCallback buildStepCallback(WebSocketSession wsSession,
                                                           Long userId, String sessionId, WsSender sender) {
        return (stepId, agentId, success, stepData) -> {
            try {
                if (!success) {
                    log.debug("[WS] step {} ({}) failed", stepId, agentId);
                    sender.sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                            .put("agent", agentId)
                            .put("label", "执行失败")
                            .put("status", "failed")
                            .toString());
                    return;
                }
                sender.sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                        .put("agent", agentId)
                        .put("label", "已完成")
                        .put("status", "done")
                        .toString());

                if (stepData != null) {
                    sendStepArtifacts(wsSession, agentId, stepData, userId, sender);
                }

                // 只把最终产出步骤的内容发给用户，中间分析步骤（画像/内容分析等）不发
                if (success && stepData != null
                        && !agentId.contains("profile")
                        && !agentId.contains("content_analysis")
                        && !agentId.contains("quality")
                        && !agentId.contains("knowledge_state")
                        && !agentId.contains("difficulty")
                        && !agentId.contains("learning_style")) {
                    String stepContent = extractStepContent(agentId, stepData);
                    if (stepContent != null && !stepContent.isBlank()) {
                        sender.sendEvent(wsSession, "chunk", stepContent);
                    }
                }
            } catch (Exception e) {
                log.error("[WS] step callback error: {}", e.getMessage(), e);
            }
        };
    }

    private void sendStepArtifacts(WebSocketSession wsSession, String agentId,
                                   Map<String, Object> stepData, Long userId, WsSender sender) {
        // 学习路径 artifact
        if (agentId.contains("learning_path")) {
            try {
                java.util.Optional<LearningPathDto> pathOpt = learningPathService.getCurrentPath(userId);
                if (pathOpt.isPresent()) {
                    LearningPathDto pathDto = pathOpt.get();
                    var payloadNode = objectMapper.createObjectNode()
                            .put("goal", pathDto.getGoal())
                            .put("planDescription", pathDto.getPlanDescription() != null ? pathDto.getPlanDescription() : "");
                    payloadNode.set("nodes", objectMapper.valueToTree(pathDto.getNodes()));
                    sender.sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                            .put("kind", "learning_path")
                            .set("payload", payloadNode)
                            .toString());
                }
            } catch (Exception e) {
                log.warn("[WS] async: failed to fetch learning path: {}", e.getMessage());
            }
        }

        // 图表 artifact
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
                sender.sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                        .put("kind", "diagram")
                        .set("payload", diagPayload)
                        .toString());
            }
        }

        // RAG 引用来源 artifact
        Object ragSourcesObj = stepData.get("ragSources");
        if (ragSourcesObj instanceof java.util.List<?> sourcesList && !sourcesList.isEmpty()) {
            try {
                var sourcesArray = objectMapper.valueToTree(sourcesList);
                sender.sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                        .put("kind", "rag_sources")
                        .set("payload", sourcesArray)
                        .toString());
            } catch (Exception e) {
                log.warn("[WS] async: failed to send rag_sources artifact: {}", e.getMessage());
            }
        }
    }

    private void handlePipelineSuccess(WebSocketSession session, PipelineResult pipelineResult,
                                       Long userId, String sessionId, String agent, WsSender sender) {
        // 步骤回调已逐步发送了 chunk + agent_step + artifact，这里只发 done 结束消息
        // 拼合所有步骤内容仅用于记忆存储，不再重复发给前端
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

        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "orchestrator")
                .put("label", "全部完成")
                .put("status", "done")
                .toString());
        sender.sendEvent(session, "done", objectMapper.createObjectNode()
                .put("session_id", sessionId)
                .toString());
        agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), finalContent, agent);
        triggerProfileExtractionAsync(userId, sessionId, session, sender);
    }

    private void handlePipelineFailure(WebSocketSession session, PipelineResult pipelineResult,
                                       Long userId, String sessionId, String content,
                                       String pipelineId, WsSender sender) {
        String errorMsg = pipelineResult.getErrorMessage();

        // 学习路径 pipeline 回退：直接生成
        if ("learning_path".equals(pipelineId)) {
            try {
                LearningPathDto pathDto = learningPathService.generatePath(userId, content, null);
                String planText = pathDto.getPlanDescription() != null ? pathDto.getPlanDescription() : "学习路径已生成";
                var payloadNode = objectMapper.createObjectNode()
                        .put("kind", "learning_path")
                        .put("goal", content);
                sender.sendEvent(session, "artifact", payloadNode.toString());
                sender.sendEvent(session, "chunk", planText);
                sender.sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", sessionId)
                        .toString());
                agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                        planText, "learning_path_fallback");
                triggerProfileExtractionAsync(userId, sessionId, session, sender);
                return;
            } catch (Exception e2) {
                log.warn("[WS] learning path fallback also failed: {}", e2.getMessage());
            }
        }

        // 图表生成 pipeline 回退
        if ("diagram".equals(pipelineId)) {
            try {
                String provider = runtimeConfig.get("image.binding", "mock");
                String apiKey = runtimeConfig.get("image.api-key", "");
                boolean keySet = !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder");
                Map<String, Object> promptResult = promptGenerationService.generatePrompt(content, "image");
                String imagePrompt = (String) promptResult.getOrDefault("prompt", content);
                String imageUrl = mediaGenerationService.generateImageByPrompt(imagePrompt);
                boolean isMock = imageUrl.startsWith("data:");

                var imgPayload = objectMapper.createObjectNode()
                        .put("url", imageUrl)
                        .put("prompt", imagePrompt)
                        .put("mediaType", "image");
                sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "diagram")
                        .put("label", "示意图已生成")
                        .put("status", "done")
                        .toString());
                sender.sendEvent(session, "artifact", objectMapper.createObjectNode()
                        .put("kind", "media_image")
                        .set("payload", imgPayload)
                        .toString());
                String mode = isMock ? "占位图（provider=" + provider + ", key=" + (keySet ? "已配置" : "未配置") + "）" : "真实图片";
                String msg = "已生成示意图 —— " + mode + "\n\n提示词：" + imagePrompt;
                sender.sendEvent(session, "chunk", msg);
                sender.sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", sessionId)
                        .toString());
                agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                        msg, "diagram_fallback_image");
                triggerProfileExtractionAsync(userId, sessionId, session, sender);
                return;
            } catch (Exception e2) {
                log.warn("[WS] diagram image fallback also failed: {}", e2.getMessage());
            }
        }

        // 资源生成 pipeline 回退
        if ("resource".equals(pipelineId)) {
            try {
                String resp = llmClient.chat(
                        "你是一个学习资源生成专家。根据用户的需求生成结构化的学习资源，包含：讲义、代码示例（如需）、练习题等。",
                        java.util.List.of(java.util.Map.of("role", "user", "content", content)),
                        null
                ).content();
                sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "resource")
                        .put("label", "资源已生成")
                        .put("status", "done")
                        .toString());
                sender.sendEvent(session, "chunk", resp);
                sender.sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", sessionId)
                        .toString());
                agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                        resp, "resource_fallback");
                triggerProfileExtractionAsync(userId, sessionId, session, sender);
                return;
            } catch (Exception e2) {
                log.warn("[WS] resource fallback also failed: {}", e2.getMessage());
            }
        }

        // 媒体生成 pipeline 回退
        if ("media_gen".equals(pipelineId)) {
            try {
                String provider = runtimeConfig.get("image.binding", "mock");
                String apiKey = runtimeConfig.get("image.api-key", "");
                boolean keySet = !apiKey.isBlank() && !apiKey.startsWith("sk-placeholder");
                Map<String, Object> promptResult = promptGenerationService.generatePrompt(content, "image");
                String imagePrompt = (String) promptResult.getOrDefault("prompt", content);
                String imageUrl = mediaGenerationService.generateImageByPrompt(imagePrompt);
                boolean isMock = imageUrl.startsWith("data:");
                var payload = objectMapper.createObjectNode()
                        .put("url", imageUrl)
                        .put("prompt", imagePrompt)
                        .put("mediaType", "image");
                sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "media_gen")
                        .put("label", "示意图已生成")
                        .put("status", "done")
                        .toString());
                sender.sendEvent(session, "artifact", objectMapper.createObjectNode()
                        .put("kind", "media_image")
                        .set("payload", payload)
                        .toString());
                String mode = isMock ? "占位图（provider=" + provider + ", key=" + (keySet ? "已配置" : "未配置") + "）" : "真实图片";
                String msg = "已生成示意图 —— " + mode + "\n\n提示词：" + imagePrompt;
                sender.sendEvent(session, "chunk", msg);
                sender.sendEvent(session, "done", objectMapper.createObjectNode()
                        .put("session_id", sessionId)
                        .toString());
                agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                        msg, "media_fallback");
                triggerProfileExtractionAsync(userId, sessionId, session, sender);
                return;
            } catch (Exception e2) {
                log.warn("[WS] media fallback also failed: {}", e2.getMessage());
            }
        }

        // 兜底：回退到 QA
        String fallbackResp = fallbackToQa(userId, content);
        if (fallbackResp != null && !fallbackResp.isBlank()) {
            sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
                    .put("label", "回答完成")
                    .put("status", "done")
                    .toString());
            sender.sendEvent(session, "chunk", fallbackResp);
            sender.sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .toString());
            agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                    fallbackResp, "qa_fallback");
            triggerProfileExtractionAsync(userId, sessionId, session, sender);
        } else {
            sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", pipelineId)
                    .put("label", "执行失败")
                    .put("status", "failed")
                    .toString());
            sender.sendEvent(session, "chunk", "抱歉，任务执行失败：" + errorMsg);
            sender.sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .toString());
            agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                    "抱歉，任务执行失败：" + errorMsg, pipelineId);
        }
    }

    // ==================== QA ====================

    public String handleQa(Long userId, String content, String sessionId) {
        // 优先 PipelineEngine
        try {
            PipelineConfig qaConfig = PipelineTemplates.questionAnswer();
            TaskContext context = new TaskContext("qa-" + System.currentTimeMillis(),
                    String.valueOf(userId), sessionId, content);
            PipelineResult pipelineResult = pipelineEngine.execute(qaConfig, context);
            if (pipelineResult.isSuccess()) {
                return orchestratorCore.aggregateResults(qaConfig, pipelineResult);
            }
            log.warn("[QA] pipeline failed: {}, falling back", pipelineResult.getErrorMessage());
        } catch (Exception e) {
            log.warn("[QA] pipeline exception: {}, falling back", e.getMessage());
        }

        // 回退：直接调用 QaAgent
        return fallbackToQa(userId, content);
    }

    private String fallbackToQa(Long userId, String content) {
        try {
            List<AgentMemory.MemoryEntry> recentHistory = agentMemory.getRecentHistory(userId, 10);
            List<Map<String, Object>> qaHistory = new java.util.ArrayList<>();
            for (var e : recentHistory) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("role", e.getRole());
                m.put("content", e.getContent());
                qaHistory.add(m);
            }
            AgentMessage agentRequest = AgentMessage.request("qa", "ws_handler", "qa_agent",
                    Map.of("goal", content));
            AgentMessage agentResponse = qaAgent.processWithHistory(agentRequest, qaHistory);
            if (agentResponse.getPerformative() == Performative.INFORM) {
                Object respContent = agentResponse.getContent().get("content");
                return respContent != null ? respContent.toString() : null;
            }
        } catch (Exception e) {
            log.error("[QA] fallback also failed: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 画像提取 ====================

    public void triggerProfileExtractionAsync(Long userId, String chatSessionId,
                                               WebSocketSession ws, WsSender sender) {
        CompletableFuture.runAsync(() -> {
            try {
                sender.sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "正在从对话更新学习画像…")
                        .put("status", "running")
                        .toString());
                learnerProfileService.extractFromSession(userId, chatSessionId);
                sender.sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "学习画像已更新")
                        .put("status", "done")
                        .toString());
            } catch (Exception e) {
                log.warn("[WS] 画像抽取失败: userId={}, sessionId={}", userId, chatSessionId, e);
                sender.sendEvent(ws, "agent_step", objectMapper.createObjectNode()
                        .put("agent", "learner_profile")
                        .put("label", "画像更新已跳过")
                        .put("status", "done")
                        .toString());
            }
        });
    }

    // ==================== 工具方法 ====================

    public String extractStepContent(String agentId, Map<String, Object> data) {
        if (data.containsKey("content")) {
            Object content = data.get("content");
            if (content != null && !String.valueOf(content).isBlank()) {
                return String.valueOf(content);
            }
        }
        if (data.containsKey("summary")) return String.valueOf(data.get("summary"));
        if (data.containsKey("result")) return String.valueOf(data.get("result"));
        if (data.containsKey("llm_analysis")) return String.valueOf(data.get("llm_analysis"));
        if (agentId.contains("learning_path") && data.containsKey("nodes")) {
            return "学习路径已生成，包含 " + data.get("nodeCount") + " 个节点。";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractRagSourcesFromPipeline(PipelineResult pipelineResult) {
        List<Map<String, Object>> allSources = new java.util.ArrayList<>();
        if (pipelineResult.getStepResults() == null) return allSources;
        for (StepResult sr : pipelineResult.getStepResults()) {
            if (!sr.isSuccess() || sr.getData() == null) continue;
            Object sourcesObj = sr.getData().get("ragSources");
            if (sourcesObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        allSources.add((Map<String, Object>) map);
                    }
                }
            }
        }
        return allSources;
    }

    // ==================== 委托 OrchestratorCore 的方法 ====================

    public PlanResult planOnly(Long userId, String content, String chatHistory) {
        return orchestratorCore.planOnly(String.valueOf(userId), content, chatHistory);
    }

    public Map<String, Object> handleSimpleRequest(PlanIntent intent, String content) {
        return orchestratorCore.handleSimpleRequest(intent, content);
    }

    // ==================== QA 路由 ====================

    public void handleQa(WebSocketSession session, Long userId, String sessionId,
                         String content, String chatHistory, WsSender sender) {
        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "intelligent_qa")
                .put("label", "正在理解问题...")
                .put("status", "running")
                .toString());

        String qaResponse = null;
        boolean pipelineSucceeded = false;
        List<Map<String, Object>> ragSources = List.of();

        // 优先 PipelineEngine（带步骤回调，逐步推送内容）
        try {
            PipelineConfig qaConfig = PipelineTemplates.questionAnswer();
            TaskContext context = new TaskContext("qa-" + System.currentTimeMillis(),
                    String.valueOf(userId), sessionId, content);

            // 使用带回调的执行，每步完成即时推送 chunk + agent_step
            StringBuilder streamedContent = new StringBuilder();
            PipelineResult pipelineResult = pipelineEngine.execute(qaConfig, context, (stepId, agentId, success, stepData) -> {
                try {
                    if (!success) {
                        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                                .put("agent", agentId)
                                .put("label", "执行失败")
                                .put("status", "failed")
                                .toString());
                        return;
                    }
                    sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                            .put("agent", agentId)
                            .put("label", "已完成")
                            .put("status", "done")
                            .toString());

                    if (stepData != null) {
                        Object ragObj = stepData.get("ragSources");
                        if (ragObj instanceof java.util.List<?> list && !list.isEmpty()) {
                            try {
                                var sourcesArray = objectMapper.valueToTree(list);
                                sender.sendEvent(session, "artifact", objectMapper.createObjectNode()
                                        .put("kind", "rag_sources")
                                        .set("payload", sourcesArray)
                                        .toString());
                            } catch (Exception e) {
                                log.warn("[WS] QA step: failed to send rag_sources: {}", e.getMessage());
                            }
                        }

                        // 只把最终产出步骤的内容发给用户，中间分析步骤不发
                        if (!agentId.contains("profile")
                                && !agentId.contains("content_analysis")
                                && !agentId.contains("quality")
                                && !agentId.contains("knowledge_state")
                                && !agentId.contains("difficulty")
                                && !agentId.contains("learning_style")) {
                            String stepContent = extractStepContent(agentId, stepData);
                            if (stepContent != null && !stepContent.isBlank()) {
                                sender.sendEvent(session, "chunk", stepContent);
                                synchronized (streamedContent) {
                                    if (streamedContent.length() > 0) streamedContent.append("\n\n");
                                    streamedContent.append(stepContent);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("[WS] QA step callback error: {}", e.getMessage(), e);
                }
            });

            if (pipelineResult.isSuccess()) {
                pipelineSucceeded = true;
                synchronized (streamedContent) {
                    qaResponse = streamedContent.isEmpty() ? null : streamedContent.toString();
                }
                ragSources = extractRagSourcesFromPipeline(pipelineResult);
            } else {
                log.warn("[WS] QA pipeline failed: {}, falling back", pipelineResult.getErrorMessage());
            }
        } catch (Exception e) {
            log.warn("[WS] QA pipeline exception: {}, falling back", e.getMessage());
        }

        // 回退：直接调用 QaAgent
        if (!pipelineSucceeded) {
            try {
                List<AgentMemory.MemoryEntry> recentHistory = agentMemory.getRecentHistory(userId, 10);
                List<Map<String, Object>> qaHistory = new java.util.ArrayList<>();
                for (var e : recentHistory) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
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
                    Object sourcesObj = agentResponse.getContent().get("ragSources");
                    if (sourcesObj instanceof List<?> list) {
                        ragSources = list.stream()
                                .filter(item -> item instanceof Map<?, ?>)
                                .map(item -> {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> m = (Map<String, Object>) item;
                                    return m;
                                })
                                .toList();
                    }
                }
            } catch (Exception e) {
                log.error("[WS] QA fallback also failed: {}", e.getMessage());
            }
        }

        // 发送结果
        if (qaResponse != null && !qaResponse.isBlank()) {
            sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
                    .put("label", "回答完成")
                    .put("status", "done")
                    .toString());

            // Pipeline 成功时已在回调中逐步发了 chunk，不需要重复发
            // 只有 QaAgent 回退时才需要在这里一次性发
            if (!pipelineSucceeded) {
                sender.sendEvent(session, "chunk", qaResponse);
            }

            // RAG 引用来源
            if (!ragSources.isEmpty()) {
                try {
                    var sourcesArray = objectMapper.valueToTree(ragSources);
                    sender.sendEvent(session, "artifact", objectMapper.createObjectNode()
                            .put("kind", "rag_sources")
                            .set("payload", sourcesArray)
                            .toString());
                } catch (Exception e) {
                    log.warn("[WS] failed to send rag_sources: {}", e.getMessage());
                }
            }

            sender.sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .toString());
            agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), qaResponse,
                    pipelineSucceeded ? "pipeline_qa" : "qa_agent");
            triggerProfileExtractionAsync(userId, sessionId, session, sender);
        } else {
            sender.sendEvent(session, "chunk", "抱歉，我暂时无法回答这个问题。");
            sender.sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .toString());
        }
    }

}
