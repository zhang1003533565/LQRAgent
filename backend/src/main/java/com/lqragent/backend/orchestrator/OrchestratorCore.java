package com.lqragent.backend.orchestrator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.chat.service.AgentRunLogService;
import com.lqragent.backend.orchestrator.capability.AgentCapability;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.planning.PlanIntent;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.planning.PlanningAgent;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrator 调度中枢 v3
 * 接收前端请求 → PlanningAgent LLM拆解 → PipelineEngine DAG执行 → 聚合返回
 * <p>
 * v3 变更：集成 CapabilityRegistry，实现动态能力发现和智能路由
 */
@Slf4j
@Service("orchestratorCore")
public class OrchestratorCore {

    private final RedisStreamsService streams;
    private final AgentRunLogService runLogService;
    private final LlmClient llmClient;
    private final AgentMemory agentMemory;
    private final PlanningAgent planningAgent;
    private final PipelineEngine pipelineEngine;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    // 任务状态跟踪（用于 learn 流程的 WebSocket 推送）
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    public OrchestratorCore(RedisStreamsService streams, AgentRunLogService runLogService,
                           LlmClient llmClient, AgentMemory agentMemory,
                           PlanningAgent planningAgent, PipelineEngine pipelineEngine,
                           CapabilityRegistry capabilityRegistry) {
        this.streams = streams;
        this.runLogService = runLogService;
        this.llmClient = llmClient;
        this.agentMemory = agentMemory;
        this.planningAgent = planningAgent;
        this.pipelineEngine = pipelineEngine;
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerOrchestrator() {
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.ORCHESTRATOR)
                .displayName("调度中枢")
                .description("统一调度中心：任务分解、Agent编排、结果聚合")
                .actions(List.of("route", "plan", "execute", "aggregate"))
                .tags(Set.of("orchestrator", "scheduler", "coordinator"))
                .build());
        log.info("[OrchestratorCore] registered orchestrator and {} agent capabilities",
                capabilityRegistry.size());
    }

    // ==================== 核心入口 ====================

    /**
     * 统一消息入口：PlanningAgent 拆解 → PipelineEngine 执行
     * 简单请求（问候/帮助）直接返回响应Map
     * QA请求返回 qa 路由（由 ChatWebSocketHandler 走流式通道）
     * 复杂请求交给 PipelineEngine 并行执行
     */
    public Map<String, Object> handleChatMessage(String userId, String message) {
        log.info("[Orchestrator] chat message: userId={}, msg={}", userId, message);

        // Step 1: PlanningAgent LLM 拆解
        PlanResult plan = planningAgent.decompose(message, userId);

        if (plan.isSimple()) {
            return handleSimpleRequest(plan.intent(), message);
        }

        // Step 2: 复杂请求 → PipelineEngine DAG 执行
        if (plan.isPipeline() && plan.pipelineConfig() != null) {
            return handlePipelineRequest(plan, userId, message);
        }

        // 兜底：QA
        return Map.of("route", "qa", "agent", AgentIds.QA, "message", message);
    }

    /**
     * 完整消息处理（用于 WebSocket 场景，含结果推送）
     */
    public void handleMessage(String userId, String sessionId, String message,
                              WebSocketSession session,
                              java.util.function.Consumer<Map<String, Object>> eventCallback) {
        log.info("[Orchestrator] handleMessage: userId={}, msg={}", userId, message);

        PlanResult plan = planningAgent.decompose(message, userId);

        if (plan.isSimple()) {
            Map<String, Object> simpleResult = handleSimpleRequest(plan.intent(), message);
            eventCallback.accept(simpleResult);
            return;
        }

        if (plan.isPipeline() && plan.pipelineConfig() != null) {
            executePipelineAndNotify(plan, userId, sessionId, message, session, eventCallback);
            return;
        }

        // 兜底
        eventCallback.accept(Map.of("route", "qa", "agent", AgentIds.QA, "message", message));
    }

    // ==================== 简单请求处理 ====================

    private Map<String, Object> handleSimpleRequest(PlanIntent intent, String message) {
        return switch (intent) {
            case GREETING -> Map.of("route", "direct", "response",
                    "你好！我是 LQRAgent 智能学习助手，可以帮你解答问题、规划学习路径、生成学习资源。请问有什么可以帮助你的？");
            case HELP -> Map.of("route", "direct", "response", buildHelpMessage());
            case LEARNING_PATH -> Map.of("route", "learning_path", "agent",
                    capabilityRegistry.matchBestAgent("learning_path"));
            case RESOURCE -> Map.of("route", "resource", "agent",
                    capabilityRegistry.matchBestAgent("resource"));
            case QUIZ -> Map.of("route", "resource", "agent",
                    capabilityRegistry.matchBestAgent("quiz"));
            case DIAGRAM -> Map.of("route", "diagram", "agent", AgentIds.DIAGRAM);
            case SUMMARY -> Map.of("route", "summary", "agent", AgentIds.SUMMARY);
            case RECOMMENDATION -> Map.of("route", "recommendation", "agent",
                    capabilityRegistry.matchBestAgent("recommendation"));
            case ASSESSMENT -> Map.of("route", "assessment", "agent",
                    capabilityRegistry.matchBestAgent("assessment"));
            case PROFILE -> Map.of("route", "profile", "agent", "profile_agent", "message", message);
            default -> Map.of("route", "qa", "agent", AgentIds.QA, "message", message);
        };
    }

    /**
     * 从 CapabilityRegistry 动态构建帮助信息
     */
    private String buildHelpMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("我可以帮你做这些事情：\n");
        int idx = 1;
        for (AgentCapability cap : capabilityRegistry.getAllCapabilities()) {
            if (cap.agentId().equals(AgentIds.ORCHESTRATOR)) continue;
            sb.append(idx++).append(". ").append(cap.description()).append("\n");
        }
        sb.append("\n请问有什么可以帮助你的？");
        return sb.toString();
    }

    // ==================== Pipeline 执行 ====================

    /**
     * 执行 Pipeline 并返回同步结果（用于 /agent-test 等 HTTP 接口）
     */
    private Map<String, Object> handlePipelineRequest(PlanResult plan, String userId, String message) {
        String taskId = UUID.randomUUID().toString();
        PipelineConfig config = plan.pipelineConfig();

        log.info("[Orchestrator] executing pipeline: {}, steps={}",
                config.getName(), config.getSteps().size());

        TaskContext context = new TaskContext(taskId, userId, null, message);
        PipelineResult result = pipelineEngine.execute(config, context);

        if (result.isSuccess()) {
            // 聚合所有步骤结果
            String aggregated = aggregateResults(config, result);
            return Map.of(
                    "route", "pipeline_complete",
                    "agent", "pipeline_engine",
                    "response", aggregated,
                    "stepResults", result.getStepResults() != null
                            ? result.getStepResults().stream()
                                .map(sr -> Map.of("stepId", sr.getStepId(), "agentId", sr.getAgentId(), "success", sr.isSuccess()))
                                .toList()
                            : List.of(),
                    "durationMs", result.getTotalDurationMs()
            );
        } else {
            return Map.of(
                    "route", "pipeline_error",
                    "agent", "pipeline_engine",
                    "response", "任务执行失败: " + result.getErrorMessage(),
                    "error", result.getErrorMessage()
            );
        }
    }

    /**
     * 执行 Pipeline 并通过 WebSocket 推送进度（用于 ChatWebSocketHandler）
     */
    private void executePipelineAndNotify(PlanResult plan, String userId, String sessionId,
                                          String message, WebSocketSession session,
                                          java.util.function.Consumer<Map<String, Object>> eventCallback) {
        String taskId = UUID.randomUUID().toString();
        PipelineConfig config = plan.pipelineConfig();

        log.info("[Orchestrator] pipeline execute: {}, steps={}", config.getName(), config.getSteps().size());

        // 推送进度：准备开始
        eventCallback.accept(Map.of(
                "type", "pipeline_start",
                "pipelineName", config.getName(),
                "stepCount", config.getSteps().size(),
                "steps", config.getSteps().stream()
                        .map(s -> Map.of("stepId", s.getStepId(), "agentId", s.getAgentId(), "action", s.getAction()))
                        .toList()
        ));

        TaskContext context = new TaskContext(taskId, userId, sessionId, message);

        // 在后台线程执行 Pipeline，不阻塞 WebSocket 线程
        Thread executor = new Thread(() -> {
            try {
                PipelineResult result = pipelineEngine.execute(config, context);

                if (result.isSuccess()) {
                    String aggregated = aggregateResults(config, result);
                    eventCallback.accept(Map.of(
                            "type", "pipeline_complete",
                            "pipelineName", config.getName(),
                            "response", aggregated,
                            "route", "pipeline_complete",
                            "durationMs", result.getTotalDurationMs()
                    ));
                } else {
                    eventCallback.accept(Map.of(
                            "type", "pipeline_error",
                            "error", result.getErrorMessage()
                    ));
                }
            } catch (Exception e) {
                log.error("[Orchestrator] pipeline execution failed: {}", e.getMessage(), e);
                eventCallback.accept(Map.of(
                        "type", "pipeline_error",
                        "error", e.getMessage()
                ));
            }
        }, "pipeline-exec-" + taskId);
        executor.setDaemon(true);
        executor.start();
    }

    // ==================== 结果聚合 ====================

    /**
     * 将 Pipeline 多步骤结果聚合为自然语言
     */
    private String aggregateResults(PipelineConfig config, PipelineResult result) {
        if (result.getStepResults() == null || result.getStepResults().isEmpty()) {
            return "任务执行完成，但未获得结果。";
        }

        StringBuilder sb = new StringBuilder();
        int stepNum = 0;
        for (StepResult sr : result.getStepResults()) {
            if (!sr.isSuccess()) continue;
            stepNum++;
            Map<String, Object> data = sr.getData();
            if (data == null) continue;

            // 各 Agent 结果格式化
            String stepSummary = extractStepSummary(sr.getAgentId(), data);
            if (stepSummary != null && !stepSummary.isBlank()) {
                if (stepNum > 1) sb.append("\n\n---\n\n");
                sb.append(stepSummary);
            }
        }

        return sb.isEmpty() ? "任务执行完成。" : sb.toString();
    }

    private String extractStepSummary(String agentId, Map<String, Object> data) {
        // 优先取 summary/result 字段
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
        // 媒体生成特殊处理
        if (agentId.contains("media_gen") && data.containsKey("imageUrl")) {
            return "![生成图片](" + data.get("imageUrl") + ")";
        }
        if (agentId.contains("media_gen") && data.containsKey("videoUrl")) {
            return "视频已生成: " + data.get("videoUrl");
        }
        // 默认：返回 data 的描述
        return "步骤完成 (" + agentId + ")";
    }

    // ==================== Learn 流程（保留兼容） ====================

    /**
     * 处理用户学习请求（WebSocket learn 流程）
     */
    public String handleLearnRequest(String userId, String goal, WebSocketSession session) {
        String taskId = UUID.randomUUID().toString();
        TaskState state = new TaskState(taskId, userId, goal, session);
        tasks.put(taskId, state);

        log.info("[Orchestrator] new learn task: {}, goal={}", taskId, goal);

        // 使用 learning_path 模板 Pipeline
        PipelineConfig config = com.lqragent.backend.orchestrator.pipeline.PipelineTemplates.learningPath();
        TaskContext context = new TaskContext(taskId, userId, null, goal);

        // 在后台线程执行
        Thread executor = new Thread(() -> {
            try {
                PipelineResult result = pipelineEngine.execute(config, context);
                if (result.isSuccess()) {
                    Map<String, Object> output = result.getOutput() != null
                            ? result.getOutput() : Map.of();
                    sendToFrontend(session, Map.of(
                            "type", "complete",
                            "taskId", taskId,
                            "results", output
                    ));
                } else {
                    sendToFrontend(session, Map.of(
                            "type", "error",
                            "taskId", taskId,
                            "message", result.getErrorMessage()
                    ));
                }
            } catch (Exception e) {
                log.error("[Orchestrator] learn pipeline failed: {}", e.getMessage(), e);
                sendToFrontend(session, Map.of(
                        "type", "error",
                        "taskId", taskId,
                        "message", e.getMessage()
                ));
            } finally {
                tasks.remove(taskId);
            }
        }, "learn-exec-" + taskId);
        executor.setDaemon(true);
        executor.start();

        return taskId;
    }

    // ==================== 工具方法 ====================

    private void sendTask(String taskId, String agentId, String action, Map<String, Object> payload) {
        Map<String, Object> content = new HashMap<>(payload);
        content.put("action", action);
        AgentMessage msg = AgentMessage.request(taskId, AgentIds.ORCHESTRATOR, agentId, content);
        streams.send("stream:agent:" + agentId, msg);
        log.info("[Orchestrator] sent task to {}: {}", agentId, action);
    }

    private void sendToFrontend(WebSocketSession session, Map<String, Object> data) {
        try {
            if (session != null && session.isOpen()) {
                String json = mapper.writeValueAsString(data);
                session.sendMessage(new org.springframework.web.socket.TextMessage(json));
            }
        } catch (Exception e) {
            log.error("[Orchestrator] send to frontend failed: {}", e.getMessage());
        }
    }

    /**
     * 任务状态内部类
     */
    private static class TaskState {
        final String taskId;
        final String userId;
        final String goal;
        final WebSocketSession session;
        final Map<String, Map<String, Object>> results = new ConcurrentHashMap<>();
        volatile boolean complete = false;

        TaskState(String taskId, String userId, String goal, WebSocketSession session) {
            this.taskId = taskId;
            this.userId = userId;
            this.goal = goal;
            this.session = session;
        }

        boolean isComplete() { return complete; }
        void setComplete(boolean c) { this.complete = c; }
    }
}