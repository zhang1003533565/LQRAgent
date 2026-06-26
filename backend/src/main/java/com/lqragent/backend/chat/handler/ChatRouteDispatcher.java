package com.lqragent.backend.chat.handler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.orchestrator.artifact.Artifact;
import com.lqragent.backend.orchestrator.artifact.ArtifactExtractor;
import com.lqragent.backend.orchestrator.artifact.ArtifactKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.pipeline.StepStreamPolicy;
import com.lqragent.backend.orchestrator.planning.PlanIntent;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.planning.PlanningSessionState;
import com.lqragent.backend.orchestrator.planning.PlanningSessionStateStore;
import com.lqragent.backend.orchestrator.consultation.ConsultationEngine;
import com.lqragent.backend.orchestrator.consultation.ConsultationListener;
import com.lqragent.backend.orchestrator.consultation.ConsultationScene;
import com.lqragent.backend.orchestrator.consultation.StopReason;
import com.lqragent.backend.agents.base.StreamSink;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

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
    private final PlanningSessionStateStore planningSessionStateStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String ORCHESTRATOR_STEP_ID = "orchestrator";

    /** Pipeline stepId → 前端展示名（与 frontend STEP_LABELS 对齐） */
    private static final Map<String, String> PIPELINE_STEP_LABELS = Map.ofEntries(
            Map.entry("profile", "获取画像"),
            Map.entry("path_gen", "生成路径"),
            Map.entry("path_consult", "路径协商"),
            Map.entry("quiz_consult", "出题协商"),
            Map.entry("resources", "生成资源"),
            Map.entry("effect", "效果评估"),
            Map.entry("quality", "质量检查"),
            Map.entry("resource", "资源生成"),
            Map.entry("diagram", "图表生成"),
            Map.entry("media_gen", "媒体生成"),
            Map.entry("prompt_gen", "Prompt 生成"),
            Map.entry("assessment", "评估批改"),
            Map.entry("summary", "总结生成"),
            Map.entry("quiz", "题目生成"),
            Map.entry("recommendation", "个性化推荐"),
            Map.entry("intervention", "学习干预"),
            Map.entry("knowledge", "知识检索"),
            Map.entry("analysis", "内容分析"),
            Map.entry("answer", "生成回答"),
            Map.entry("path_adjust", "路径调整"),
            Map.entry("resource_push", "资源推送"),
            Map.entry("content_analysis", "内容分析"),
            Map.entry("qa", "生成讲解")
    );

    public ChatRouteDispatcher(
            OrchestratorCore orchestratorCore,
            PipelineEngine pipelineEngine,
            QaAgent qaAgent,
            AgentMemory agentMemory,
            LearnerProfileService learnerProfileService,
            LlmClient llmClient,
            MediaGenerationService mediaGenerationService,
            PromptGenerationService promptGenerationService,
            LearningPathService learningPathService,
            AppRuntimeConfig runtimeConfig,
            PlanningSessionStateStore planningSessionStateStore) {
        this.orchestratorCore = orchestratorCore;
        this.pipelineEngine = pipelineEngine;
        this.qaAgent = qaAgent;
        this.agentMemory = agentMemory;
        this.learnerProfileService = learnerProfileService;
        this.llmClient = llmClient;
        this.mediaGenerationService = mediaGenerationService;
        this.promptGenerationService = promptGenerationService;
        this.learningPathService = learningPathService;
        this.runtimeConfig = runtimeConfig;
        this.planningSessionStateStore = planningSessionStateStore;
    }

    // ==================== 层 3：资源按需生成 ====================

    /** 用户确认后，基于已有路径跑 resources-only pipeline */
    public void handleResourceFollowUp(WebSocketSession session, Long userId, String sessionId,
                                       String originalGoal, WsSender sender) {
        AtomicBoolean contentStreamed = new AtomicBoolean(false);
        try {
            java.util.Optional<LearningPathDto> pathOpt = learningPathService.getCurrentPath(userId);
            if (pathOpt.isEmpty()) {
                sender.sendEvent(session, "chunk", "未找到可用的学习路径，请先重新规划学习路径。");
                sendDone(session, sessionId, null, sender);
                return;
            }

            PipelineConfig config = PipelineTemplates.learningPathResourcesOnly();
            if (runtimeConfig.isStreamProgressEnabled()) {
                sendPipelineStart(session, config, sender);
            }

            TaskContext context = new TaskContext(
                    "resources-" + System.currentTimeMillis(),
                    String.valueOf(userId), sessionId,
                    originalGoal != null ? originalGoal : pathOpt.get().getGoal());
            context.setResult("path_gen", buildPathGenResult(pathOpt.get()));
            context.setResult("path_consult", buildPathGenResult(pathOpt.get()));

            PipelineResult pipelineResult = pipelineEngine.execute(config, context,
                    buildStepCallback(session, userId, sessionId, sender, contentStreamed));

            if (pipelineResult.isSuccess()) {
                handlePipelineSuccess(session, pipelineResult, userId, sessionId,
                        config.getPipelineId(), originalGoal, sender, contentStreamed);
            } else {
                handlePipelineFailure(session, pipelineResult, userId, sessionId, originalGoal,
                        config.getPipelineId(), sender);
            }
        } catch (Exception e) {
            log.error("[WS] resource follow-up error: {}", e.getMessage(), e);
            sender.sendEvent(session, "chunk", "抱歉，讲义生成失败：" + e.getMessage());
            sendDone(session, sessionId, null, sender);
            agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                    "抱歉，讲义生成失败：" + e.getMessage(), "learning_path_resources");
        }
    }

    /** 用户拒绝生成资源 */
    public void handleResourceDecline(WebSocketSession session, Long userId, String sessionId, WsSender sender) {
        String msg = "好的，已跳过讲义生成。您可以随时说「生成讲义」继续。";
        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("stepId", ORCHESTRATOR_STEP_ID)
                .put("agent", ORCHESTRATOR_STEP_ID)
                .put("label", "已跳过")
                .put("status", "done")
                .toString());
        sender.sendEvent(session, "chunk", msg);
        Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), msg, "orchestrator", null);
        sendDone(session, sessionId, messageId, sender);
    }

    /** 资源确认意图不明确时重新引导 */
    public void handleResourceConfirmReminder(WebSocketSession session, Long userId, String sessionId,
                                              WsSender sender) {
        String msg = "请回复「生成」开始生成讲义，或「不用」跳过。";
        sender.sendEvent(session, "chunk", msg);
        Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), msg, "orchestrator", null);
        sendDone(session, sessionId, messageId, sender);
    }

    private void sendPipelineStart(WebSocketSession session, PipelineConfig config, WsSender sender) {
        var root = objectMapper.createObjectNode();
        root.put("pipelineName", config.getName() != null ? config.getName() : config.getPipelineId());
        root.put("stepCount", config.getSteps() != null ? config.getSteps().size() : 0);
        root.put("taskId", UUID.randomUUID().toString());
        var stepsNode = objectMapper.createArrayNode();
        if (config.getSteps() != null) {
            for (var step : config.getSteps()) {
                var stepNode = objectMapper.createObjectNode();
                stepNode.put("stepId", step.getStepId());
                stepNode.put("agentId", step.getAgentId());
                stepNode.put("action", step.getAction() != null ? step.getAction() : "");
                stepsNode.add(stepNode);
            }
        }
        root.set("steps", stepsNode);
        sender.sendEvent(session, "pipeline_start", root.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildPathGenResult(LearningPathDto dto) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        if (dto.getGoal() != null) {
            result.put("goal", dto.getGoal());
        }
        if (dto.getNodes() != null) {
            result.put("nodes", objectMapper.convertValue(dto.getNodes(), List.class));
            result.put("nodeCount", dto.getNodes().size());
        }
        if (dto.getPlanDescription() != null && !dto.getPlanDescription().isBlank()) {
            result.put("planDescription", dto.getPlanDescription());
            result.put("content", dto.getPlanDescription());
        }
        if (dto.getPathId() != null) {
            result.put("pathId", dto.getPathId());
        }
        return result;
    }

    // ==================== CLARIFY ====================

    public void handleClarify(WebSocketSession session, PlanResult plan,
                              Long userId, String sessionId, String originalGoal, WsSender sender) {
        if (runtimeConfig.isPlanningSessionStateEnabled() && originalGoal != null && !originalGoal.isBlank()) {
            planningSessionStateStore.saveAwaitingClarify(
                    Long.parseLong(sessionId), originalGoal, plan.clarifyQuestions());
        }
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
        clarifyMsg.append("\n你按上面几点简单回复就行，我会据此帮你安排学习路径。");

        sender.sendEvent(session, "chunk", clarifyMsg.toString());
        sender.sendEvent(session, "done", objectMapper.createObjectNode()
                .put("session_id", sessionId)
                .toString());
        agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), clarifyMsg.toString(), "orchestrator");
    }

    // ==================== Pipeline ====================

    public void handlePipeline(WebSocketSession session, PlanResult plan,
                               Long userId, String sessionId, String content, WsSender sender) {
        AtomicBoolean contentStreamed = new AtomicBoolean(false);
        try {
            if (runtimeConfig.isStreamProgressEnabled()) {
                sendPipelineStart(session, plan, sender);
            }
            PipelineResult pipelineResult = orchestratorCore.handlePipelineAsync(
                    plan, String.valueOf(userId), content,
                    buildStepCallback(session, userId, sessionId, sender, contentStreamed),
                    ctx -> {
                        ctx.put(ConsultationEngine.LISTENER_KEY, buildConsultationListener(session, sender));
                        ctx.put("chat.sessionId", sessionId);
                    });

            String agent = plan.pipelineConfig().getPipelineId();

            if (pipelineResult.isSuccess()) {
                handlePipelineSuccess(session, pipelineResult, userId, sessionId, agent, content, sender,
                        contentStreamed);
            } else {
                handlePipelineFailure(session, pipelineResult, userId, sessionId, content, agent, sender);
            }
        } catch (Exception e) {
            log.error("[WS] pipeline error: {}", e.getMessage(), e);
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
    }

    private ConsultationListener buildConsultationListener(WebSocketSession session, WsSender sender) {
        return new ConsultationListener() {
            @Override
            public void onStart(ConsultationScene scene, java.util.List<String> participants, int maxRounds) {
                try {
                    var node = objectMapper.createObjectNode();
                    node.put("scene", scene.name());
                    node.put("maxRounds", maxRounds);
                    node.set("participants", objectMapper.valueToTree(participants));
                    sender.sendEvent(session, "consultation_start", node.toString());
                } catch (Exception e) {
                    log.warn("[WS] consultation_start send failed: {}", e.getMessage());
                }
            }

            @Override
            public void onRound(int round, String agentId, String role, String summary) {
                onRound(round, agentId, role, summary, null);
            }

            @Override
            public void onRound(int round, String agentId, String role, String summary, String textDelta) {
                try {
                    var node = objectMapper.createObjectNode();
                    node.put("round", round);
                    node.put("agentId", agentId);
                    node.put("role", role);
                    node.put("summary", summary != null ? summary : "");
                    if (textDelta != null && !textDelta.isBlank()) {
                        node.put("textDelta", textDelta);
                    }
                    sender.sendEvent(session, "consultation_round", node.toString());
                    if (textDelta != null && !textDelta.isBlank()) {
                        sender.sendEvent(session, "chunk", "【协商第" + round + "轮·" + role + "】" + textDelta + "\n");
                    }
                } catch (Exception e) {
                    log.warn("[WS] consultation_round send failed: {}", e.getMessage());
                }
            }

            @Override
            public void onEnd(StopReason reason, long durationMs) {
                try {
                    sender.sendEvent(session, "consultation_end", objectMapper.createObjectNode()
                            .put("stopReason", reason.name())
                            .put("durationMs", durationMs)
                            .toString());
                } catch (Exception e) {
                    log.warn("[WS] consultation_end send failed: {}", e.getMessage());
                }
            }
        };
    }

    private PipelineEngine.StepCallback buildStepCallback(WebSocketSession wsSession,
                                                           Long userId, String sessionId, WsSender sender,
                                                           AtomicBoolean contentStreamed) {
        return new PipelineEngine.StepCallback() {
            @Override
            public void onStepStart(String stepId, String agentId, String action) {
                if (runtimeConfig.isStreamProgressEnabled()) {
                    handleStepStart(wsSession, stepId, agentId, sender);
                }
            }

            @Override
            public void onStepComplete(String stepId, String agentId, boolean success,
                                       Map<String, Object> stepData) {
                handleStepComplete(wsSession, stepId, agentId, success, stepData, userId, sender,
                        contentStreamed, null);
            }
        };
    }

    private void sendPipelineStart(WebSocketSession session, PlanResult plan, WsSender sender) {
        PipelineConfig config = plan.pipelineConfig();
        if (config == null || config.getSteps() == null) {
            return;
        }
        sendPipelineStart(session, config, sender);
    }

    private void handleStepStart(WebSocketSession wsSession, String stepId, String agentId, WsSender sender) {
        String label = pipelineStepLabel(stepId, agentId) + "…";
        sender.sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                .put("stepId", stepId)
                .put("agent", agentId)
                .put("agentId", agentId)
                .put("label", label)
                .put("status", "running")
                .toString());
    }

    private String pipelineStepLabel(String stepId, String agentId) {
        if (stepId != null && PIPELINE_STEP_LABELS.containsKey(stepId)) {
            return PIPELINE_STEP_LABELS.get(stepId);
        }
        return agentId != null ? agentId : stepId;
    }

    /**
     * Pipeline 单步完成：推送 agent_step、Artifact、可选 chunk。
     * Pipeline 与 QA 路径共用，避免重复逻辑。
     *
     * @param memoryBuffer 非 null 时把已推送的 chunk 文本追加进去（供 QA 记忆存储）
     */
    private void handleStepComplete(WebSocketSession wsSession, String stepId, String agentId, boolean success,
                                    Map<String, Object> stepData, Long userId, WsSender sender,
                                    AtomicBoolean contentStreamed, StringBuilder memoryBuffer) {
        try {
            if (!success) {
                log.debug("[WS] step ({}) failed", stepId);
                sender.sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                        .put("stepId", stepId)
                        .put("agent", agentId)
                        .put("agentId", agentId)
                        .put("label", pipelineStepLabel(stepId, agentId) + "失败")
                        .put("status", "failed")
                        .toString());
                return;
            }
            sender.sendEvent(wsSession, "agent_step", objectMapper.createObjectNode()
                    .put("stepId", stepId)
                    .put("agent", agentId)
                    .put("agentId", agentId)
                    .put("label", pipelineStepLabel(stepId, agentId))
                    .put("status", "done")
                    .toString());

            if (stepData == null) {
                return;
            }

            List<Artifact> artifacts = sendStepArtifacts(wsSession, agentId, stepData, userId, sender);
            if (StepStreamPolicy.shouldStreamContent(agentId, stepData, artifacts)) {
                String stepContent = extractStepContent(agentId, stepData);
                if (stepContent != null && !stepContent.isBlank()) {
                    sender.sendEvent(wsSession, "chunk", stepContent);
                    if (contentStreamed != null) {
                        contentStreamed.set(true);
                    }
                    if (memoryBuffer != null) {
                        synchronized (memoryBuffer) {
                            if (memoryBuffer.length() > 0) {
                                memoryBuffer.append("\n\n");
                            }
                            memoryBuffer.append(stepContent);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[WS] step callback error: {}", e.getMessage(), e);
        }
    }

    /** 从步骤数据提取 Artifact 并推送 WS；返回已识别产物供流式策略判断 */
    private List<Artifact> sendStepArtifacts(WebSocketSession wsSession, String agentId,
                                             Map<String, Object> stepData, Long userId, WsSender sender) {
        List<Artifact> artifacts = ArtifactExtractor.fromStepData(agentId, stepData);
        boolean sentLearningPath = false;
        for (Artifact artifact : artifacts) {
            sendArtifactEvent(wsSession, artifact, sender);
            if (artifact.getKind() == ArtifactKind.LEARNING_PATH) {
                sentLearningPath = true;
            }
        }

        if (!sentLearningPath && AgentIds.LEARNING_PATH.equals(agentId)) {
            enrichLearningPathFromService(wsSession, userId, sender);
        }

        if (artifacts.isEmpty()) {
            log.debug("[WS] no artifacts extracted for agent {}", agentId);
        }
        return artifacts;
    }

    private void sendArtifactEvent(WebSocketSession wsSession, Artifact artifact, WsSender sender) {
        try {
            var node = objectMapper.createObjectNode();
            node.put("kind", artifact.getKind().wireCode());
            node.set("payload", ragPayloadNode(artifact));
            if (artifact.getArtifactId() != null) {
                node.put("artifactId", artifact.getArtifactId());
            }
            sender.sendEvent(wsSession, "artifact", node.toString());
        } catch (Exception e) {
            log.warn("[WS] failed to send artifact {}: {}", artifact.getKind(), e.getMessage());
        }
    }

    /** 前端 rag_sources 期望 payload 为 sources 数组，而非 {sources: [...]} */
    private JsonNode ragPayloadNode(Artifact artifact) {
        if (artifact.getKind() == ArtifactKind.RAG_SOURCES && artifact.getPayload() != null) {
            Object sources = artifact.getPayload().get("sources");
            if (sources != null) {
                return objectMapper.valueToTree(sources);
            }
        }
        return objectMapper.valueToTree(artifact.getPayload());
    }

    private void sendRagSourcesArtifact(WebSocketSession wsSession,
                                        List<Map<String, Object>> ragSources, WsSender sender) {
        if (ragSources == null || ragSources.isEmpty()) {
            return;
        }
        try {
            sender.sendEvent(wsSession, "artifact", objectMapper.createObjectNode()
                    .put("kind", "rag_sources")
                    .set("payload", objectMapper.valueToTree(ragSources))
                    .toString());
        } catch (Exception e) {
            log.warn("[WS] failed to send rag_sources: {}", e.getMessage());
        }
    }

    private Map<String, Object> ragMetadata(List<Map<String, Object>> ragSources) {
        if (ragSources == null || ragSources.isEmpty()) {
            return null;
        }
        return Map.of("ragSources", ragSources);
    }

    private Map<String, Object> mergeAssistantMetadata(PipelineResult pipelineResult,
                                                       List<Map<String, Object>> ragSources) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        Map<String, Object> ragMeta = ragMetadata(ragSources);
        if (ragMeta != null) {
            metadata.putAll(ragMeta);
        }
        metadata.putAll(extractArtifactMetadataFromPipeline(pipelineResult));
        return metadata.isEmpty() ? null : metadata;
    }

    private Map<String, Object> extractArtifactMetadataFromPipeline(PipelineResult pipelineResult) {
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        if (pipelineResult == null) {
            return meta;
        }
        for (Artifact artifact : ArtifactExtractor.collectFromPipeline(pipelineResult)) {
            mergeArtifactIntoPersistMeta(meta, artifact);
        }
        // 兜底：逐步扫描 artifactKind / url 字段（避免 List<Artifact> 类型丢失）
        if (pipelineResult.getStepResults() != null) {
            for (var sr : pipelineResult.getStepResults()) {
                if (!sr.isSuccess() || sr.getData() == null) {
                    continue;
                }
                scanStepDataForMediaMeta(sr.getData(), meta);
            }
        }
        return meta;
    }

    private void mergeArtifactIntoPersistMeta(Map<String, Object> meta, Artifact artifact) {
        if (artifact == null || artifact.getKind() == null) {
            return;
        }
        Map<String, Object> payload = artifact.getPayload();
        switch (artifact.getKind()) {
            case IMAGE -> {
                meta.put("contentType", "image");
                if (payload != null && payload.get("url") != null) {
                    meta.put("imageUrl", String.valueOf(payload.get("url")));
                }
            }
            case VIDEO -> {
                meta.put("contentType", "video");
                if (payload != null && payload.get("url") != null) {
                    meta.put("videoUrl", String.valueOf(payload.get("url")));
                }
            }
            case QUIZ -> {
                meta.put("contentType", "quiz");
                if (payload != null) {
                    meta.put("quizData", payload);
                }
            }
            case DIAGRAM -> {
                meta.put("contentType", "diagram");
                if (payload != null) {
                    if (payload.get("diagram") != null) {
                        meta.put("diagramCode", String.valueOf(payload.get("diagram")));
                    }
                    meta.put("diagramFormat", payload.get("format") != null
                            ? String.valueOf(payload.get("format")) : "mermaid");
                }
            }
            case LEARNING_PATH -> meta.put("contentType", "learning_path");
            default -> { }
        }
    }

    @SuppressWarnings("unchecked")
    private void scanStepDataForMediaMeta(Map<String, Object> data, Map<String, Object> meta) {
        Object kind = data.get("artifactKind");
        Object payloadObj = data.get("artifactPayload");
        if (payloadObj instanceof Map<?, ?> payloadMap) {
            Map<String, Object> payload = (Map<String, Object>) payloadMap;
            String wire = kind != null ? String.valueOf(kind) : "";
            if (("media_image".equals(wire) || "image".equals(wire)) && payload.get("url") != null) {
                meta.put("contentType", "image");
                meta.put("imageUrl", String.valueOf(payload.get("url")));
            } else if (("video".equals(wire) || "media_video".equals(wire)) && payload.get("url") != null) {
                meta.put("contentType", "video");
                meta.put("videoUrl", String.valueOf(payload.get("url")));
            }
        }
        for (String key : List.of("imageUrl", "mediaUrl", "url", "videoUrl")) {
            Object v = data.get(key);
            if (v == null || String.valueOf(v).isBlank()) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.startsWith("http") && !s.startsWith("data:")) {
                continue;
            }
            if ("videoUrl".equals(key) || s.endsWith(".mp4") || s.endsWith(".webm")) {
                meta.put("contentType", "video");
                meta.put("videoUrl", s);
            } else if (!meta.containsKey("imageUrl")) {
                meta.put("contentType", "image");
                meta.put("imageUrl", s);
            }
        }
    }

    private String buildAssistantPersistContent(PipelineResult pipelineResult, Map<String, Object> metadata) {
        if (metadata != null) {
            if (metadata.get("imageUrl") != null) {
                return "图片已生成。";
            }
            if (metadata.get("videoUrl") != null) {
                return "视频已生成。";
            }
            if (metadata.get("quizData") != null) {
                return "题目已生成。";
            }
            if (metadata.get("diagramCode") != null) {
                return "图表已生成。";
            }
            if ("learning_path".equals(metadata.get("contentType"))) {
                return "学习路径已生成。";
            }
        }
        StringBuilder allContent = new StringBuilder();
        List<StepResult> stepResults = pipelineResult != null ? pipelineResult.getStepResults() : null;
        if (stepResults != null) {
            for (var sr : stepResults) {
                if (!sr.isSuccess() || sr.getData() == null) {
                    continue;
                }
                String agentId = sr.getAgentId();
                if (StepStreamPolicy.isInternalStep(agentId) || StepStreamPolicy.isArtifactRenderedStep(agentId)) {
                    continue;
                }
                String stepContent = extractStepContent(agentId, sr.getData());
                if (stepContent != null && !stepContent.isBlank()
                        && !StepStreamPolicy.isStatusOnlyText(stepContent)) {
                    if (allContent.length() > 0) {
                        allContent.append("\n\n");
                    }
                    allContent.append(stepContent);
                }
            }
        }
        return allContent.isEmpty() ? "任务执行完成。" : allContent.toString();
    }

    private Map<String, Object> imageMetadata(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        Map<String, Object> meta = new java.util.LinkedHashMap<>();
        meta.put("contentType", "image");
        meta.put("imageUrl", imageUrl);
        return meta;
    }

    private void sendDone(WebSocketSession session, String sessionId, Long messageId, WsSender sender) {
        var node = objectMapper.createObjectNode().put("session_id", sessionId);
        if (messageId != null) {
            node.put("message_id", messageId);
        }
        sender.sendEvent(session, "done", node.toString());
    }

    /** LearningPath 工具未返回 nodes 时，从 DB 拉取当前路径补发 artifact */
    private void enrichLearningPathFromService(WebSocketSession wsSession, Long userId, WsSender sender) {
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

    private void handlePipelineSuccess(WebSocketSession session, PipelineResult pipelineResult,
                                       Long userId, String sessionId, String agent, String originalGoal,
                                       WsSender sender, AtomicBoolean contentStreamed) {
        // 步骤回调已逐步发送了 chunk + agent_step + artifact，这里只发 done 结束消息
        // 拼合步骤内容仅用于记忆存储：排除 prompt 工程等内部步骤，有图片时只存短文案
        Map<String, Object> metadata = mergeAssistantMetadata(pipelineResult,
                extractRagSourcesFromPipeline(pipelineResult));
        String finalContent = buildAssistantPersistContent(pipelineResult, metadata);

        if (!contentStreamed.get() && !finalContent.isBlank()
                && !(runtimeConfig.isStreamProgressEnabled() && runtimeConfig.isStreamProgressSkipFinalDump())) {
            sender.sendEvent(session, "chunk", finalContent);
        }

        // 层 3：core 路径先交付，询问是否生成讲义/资源
        if (runtimeConfig.isPathStagedDeliveryEnabled() && "learning_path_core".equals(agent)) {
            String resourcePrompt = "\n\n是否需要为您生成各节点的讲义与练习题？\n"
                    + "回复「生成」或「是的」开始，回复「不用」跳过。";
            sender.sendEvent(session, "chunk", resourcePrompt);
            if (runtimeConfig.isPlanningSessionStateEnabled() && originalGoal != null && !originalGoal.isBlank()) {
                planningSessionStateStore.saveAwaitingResourceConfirm(Long.parseLong(sessionId), originalGoal);
            }
            sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("stepId", ORCHESTRATOR_STEP_ID)
                    .put("agent", ORCHESTRATOR_STEP_ID)
                    .put("label", "路径已生成")
                    .put("status", "done")
                    .toString());
            String persistContent = finalContent.isBlank() ? "学习路径已生成。" : finalContent;
            persistContent = persistContent + resourcePrompt;
            Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), persistContent, agent,
                    metadata);
            sendDone(session, sessionId, messageId, sender);
            triggerProfileExtractionAsync(userId, sessionId, session, sender);
            return;
        }

        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("stepId", ORCHESTRATOR_STEP_ID)
                .put("agent", ORCHESTRATOR_STEP_ID)
                .put("label", "全部完成")
                .put("status", "done")
                .toString());
        // RAG artifact 已在步骤回调中推送；此处持久化 metadata（含 imageUrl 等）
        List<Map<String, Object>> ragSources = extractRagSourcesFromPipeline(pipelineResult);
        if (metadata == null) {
            metadata = mergeAssistantMetadata(pipelineResult, ragSources);
        } else if (ragSources != null && !ragSources.isEmpty()) {
            Map<String, Object> ragMeta = ragMetadata(ragSources);
            if (ragMeta != null) {
                metadata.putAll(ragMeta);
            }
        }
        Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), finalContent, agent,
                metadata);
        sendDone(session, sessionId, messageId, sender);
        triggerProfileExtractionAsync(userId, sessionId, session, sender);
    }

    private void handlePipelineFailure(WebSocketSession session, PipelineResult pipelineResult,
                                       Long userId, String sessionId, String content,
                                       String pipelineId, WsSender sender) {
        String errorMsg = pipelineResult.getErrorMessage();

        // 后续步骤失败但媒体/图表已生成：仍按成功展示
        Map<String, Object> partialMeta = extractArtifactMetadataFromPipeline(pipelineResult);
        if (partialMeta.get("imageUrl") != null || partialMeta.get("videoUrl") != null) {
            String msg = partialMeta.get("imageUrl") != null ? "图片已生成。" : "视频已生成。";
            sender.sendEvent(session, "chunk", msg);
            Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                    msg, pipelineId + "_partial", partialMeta);
            sendDone(session, sessionId, messageId, sender);
            triggerProfileExtractionAsync(userId, sessionId, session, sender);
            return;
        }

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
                Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                        msg, "diagram_fallback_image", imageMetadata(imageUrl));
                sendDone(session, sessionId, messageId, sender);
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
                Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId),
                        msg, "media_fallback", imageMetadata(imageUrl));
                sendDone(session, sessionId, messageId, sender);
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
        if (AgentIds.LEARNING_PATH.equals(agentId) && data.containsKey("nodes")) {
            return "学习路径已生成，包含 " + data.get("nodeCount") + " 个节点。";
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> extractRagSourcesFromPipeline(PipelineResult pipelineResult) {
        List<Map<String, Object>> allSources = new java.util.ArrayList<>();
        if (pipelineResult.getStepResults() == null) {
            return allSources;
        }
        for (StepResult sr : pipelineResult.getStepResults()) {
            if (!sr.isSuccess() || sr.getData() == null) {
                continue;
            }
            Map<String, Object> data = sr.getData();
            Object sourcesObj = data.get("ragSources");
            if (sourcesObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        allSources.add((Map<String, Object>) map);
                    }
                }
            }
            for (Artifact artifact : ArtifactExtractor.fromStepData(sr.getAgentId(), data)) {
                if (artifact.getKind() != ArtifactKind.RAG_SOURCES || artifact.getPayload() == null) {
                    continue;
                }
                Object nested = artifact.getPayload().get("sources");
                if (nested instanceof List<?> nestedList) {
                    for (Object item : nestedList) {
                        if (item instanceof Map<?, ?> map) {
                            allSources.add((Map<String, Object>) map);
                        }
                    }
                }
            }
        }
        return allSources;
    }

    // ==================== 委托 OrchestratorCore 的方法 ====================

    public PlanResult planOnly(Long userId, String content, String chatHistory) {
        return planOnly(userId, content, chatHistory, false);
    }

    public PlanResult planOnly(Long userId, String content, String chatHistory, boolean skipGateG1) {
        return orchestratorCore.planOnly(String.valueOf(userId), content, chatHistory, skipGateG1);
    }

    /** 读取 Clarify 会话状态（供 WebSocketHandler 合并消息） */
    public PlanningSessionState getPlanningSessionState(Long chatSessionId) {
        return planningSessionStateStore.get(chatSessionId);
    }

    /** Pipeline 成功后清除 Clarify 状态 */
    public void clearPlanningSessionState(Long chatSessionId) {
        planningSessionStateStore.clear(chatSessionId);
    }

    public Map<String, Object> handleSimpleRequest(PlanIntent intent, String content) {
        return orchestratorCore.handleSimpleRequest(intent, content);
    }

    // ==================== QA 路由 ====================

    public void handleQa(WebSocketSession session, Long userId, String sessionId,
                         String content, String chatHistory, WsSender sender) {
        if (runtimeConfig.isLlmStreamSceneEnabled("qa")) {
            handleQaStream(session, userId, sessionId, content, sender);
            return;
        }

        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "intelligent_qa")
                .put("label", "正在理解问题...")
                .put("status", "running")
                .toString());

        String qaResponse = null;
        boolean pipelineSucceeded = false;
        List<Map<String, Object>> ragSources = List.of();
        PipelineResult qaPipelineResult = null;

        // 优先 PipelineEngine（带步骤回调，逐步推送内容）
        try {
            PipelineConfig qaConfig = PipelineTemplates.questionAnswer();
            TaskContext context = new TaskContext("qa-" + System.currentTimeMillis(),
                    String.valueOf(userId), sessionId, content);

            StringBuilder streamedContent = new StringBuilder();
            AtomicBoolean qaContentStreamed = new AtomicBoolean(false);
            PipelineResult pipelineResult = pipelineEngine.execute(qaConfig, context,
                    (stepId, agentId, success, stepData) -> handleStepComplete(
                            session, stepId, agentId, success, stepData, userId, sender,
                            qaContentStreamed, streamedContent));

            if (pipelineResult.isSuccess()) {
                pipelineSucceeded = true;
                qaPipelineResult = pipelineResult;
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

            // Pipeline 路径已在步骤回调推送 rag_sources；QaAgent 回退路径在此补发
            if (!pipelineSucceeded && !ragSources.isEmpty()) {
                sendRagSourcesArtifact(session, ragSources, sender);
            }

            Map<String, Object> metadata = pipelineSucceeded && qaPipelineResult != null
                    ? mergeAssistantMetadata(qaPipelineResult, ragSources)
                    : ragMetadata(ragSources);
            Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), qaResponse,
                    pipelineSucceeded ? "pipeline_qa" : "qa_agent", metadata);
            sendDone(session, sessionId, messageId, sender);
            triggerProfileExtractionAsync(userId, sessionId, session, sender);
        } else {
            sender.sendEvent(session, "chunk", "抱歉，我暂时无法回答这个问题。");
            sender.sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .toString());
        }
    }

    /**
     * QA 直出 token 流（跳过 3 步 QA Pipeline）
     */
    private void handleQaStream(WebSocketSession session, Long userId, String sessionId,
                                String content, WsSender sender) {
        sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                .put("agent", "intelligent_qa")
                .put("label", "正在生成回答...")
                .put("status", "running")
                .toString());

        String qaResponse = null;
        List<Map<String, Object>> ragSources = List.of();

        try {
            List<AgentMemory.MemoryEntry> recentHistory = agentMemory.getRecentHistory(userId, 10);
            List<Map<String, Object>> qaHistory = new java.util.ArrayList<>();
            for (var e : recentHistory) {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("role", e.getRole());
                m.put("content", e.getContent());
                qaHistory.add(m);
            }

            Map<String, Object> reqContent = new java.util.LinkedHashMap<>(Map.of("goal", content));
            reqContent.put("userId", userId);
            AgentMessage agentRequest = AgentMessage.request("qa-stream", "ws_handler", AgentIds.QA, reqContent);

            StreamSink sink = delta -> sender.sendEvent(session, "chunk", delta);
            AgentMessage agentResponse = qaAgent.streamWithHistory(agentRequest, qaHistory, sink);

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
            log.error("[WS] QA stream failed: {}", e.getMessage(), e);
        }

        if (qaResponse != null && !qaResponse.isBlank()) {
            sender.sendEvent(session, "agent_step", objectMapper.createObjectNode()
                    .put("agent", "intelligent_qa")
                    .put("label", "回答完成")
                    .put("status", "done")
                    .toString());
            if (!ragSources.isEmpty()) {
                sendRagSourcesArtifact(session, ragSources, sender);
            }
            Map<String, Object> metadata = ragMetadata(ragSources);
            Long messageId = agentMemory.addAgentResponse(userId, Long.parseLong(sessionId), qaResponse,
                    "qa_agent_stream", metadata);
            sendDone(session, sessionId, messageId, sender);
            triggerProfileExtractionAsync(userId, sessionId, session, sender);
        } else {
            sender.sendEvent(session, "chunk", "抱歉，我暂时无法回答这个问题。");
            sender.sendEvent(session, "done", objectMapper.createObjectNode()
                    .put("session_id", sessionId)
                    .toString());
        }
    }

}
