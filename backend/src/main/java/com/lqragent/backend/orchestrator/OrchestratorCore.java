package com.lqragent.backend.orchestrator;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.chat.service.AgentRunLogService;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.artifact.Artifact;
import com.lqragent.backend.orchestrator.artifact.ArtifactExtractor;
import com.lqragent.backend.orchestrator.artifact.ArtifactKind;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.AgentCardRegistry;
import com.lqragent.backend.orchestrator.context.LearnerContextDto;
import com.lqragent.backend.orchestrator.context.LearnerContextService;
import com.lqragent.backend.orchestrator.planning.PlanningGateService;
import com.lqragent.backend.orchestrator.supervisor.SupervisorService;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.pipeline.StepStreamPolicy;
import com.lqragent.backend.orchestrator.pipeline.service.PipelineTaskService;
import com.lqragent.backend.orchestrator.planning.PlanIntent;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.planning.PlanningAgent;
import com.lqragent.backend.orchestrator.planning.TaskPlan;
import com.lqragent.backend.orchestrator.planning.TaskStep;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrator 调度中枢 v3
 * 接收前端请求 → PlanningAgent LLM拆解 → PipelineEngine DAG执行 → 聚合返回
 * <p>
 * v3 变更：AgentCardRegistry 统一能力发现；PlanningAgent + PipelineEngine 编排
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
    private final AgentCardRegistry agentCardRegistry;
    private final LearningPathService learningPathService;
    private final PipelineTaskService pipelineTaskService;
    private final LearnerContextService learnerContextService;
    private final PlanningGateService planningGateService;
    private final SupervisorService supervisorService;
    private final ObjectMapper mapper = new ObjectMapper();

    // 任务状态跟踪（用于 learn 流程的 WebSocket 推送）
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    public OrchestratorCore(RedisStreamsService streams, AgentRunLogService runLogService,
                           LlmClient llmClient, AgentMemory agentMemory,
                           PlanningAgent planningAgent, PipelineEngine pipelineEngine,
                           AgentCardRegistry agentCardRegistry,
                           LearningPathService learningPathService,
                           PipelineTaskService pipelineTaskService,
                           LearnerContextService learnerContextService,
                           PlanningGateService planningGateService,
                           SupervisorService supervisorService) {
        this.streams = streams;
        this.runLogService = runLogService;
        this.llmClient = llmClient;
        this.agentMemory = agentMemory;
        this.planningAgent = planningAgent;
        this.pipelineEngine = pipelineEngine;
        this.agentCardRegistry = agentCardRegistry;
        this.learningPathService = learningPathService;
        this.pipelineTaskService = pipelineTaskService;
        this.learnerContextService = learnerContextService;
        this.planningGateService = planningGateService;
        this.supervisorService = supervisorService;
    }

    @PostConstruct
    public void registerOrchestrator() {
        log.info("[OrchestratorCore] ready (agent cards registered at runtime: expect ~20)");
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

        // 阶段二新增：将动态 TaskPlan 转为 PipelineConfig
        if (plan.isPlan() && plan.taskPlan() != null) {
            PipelineConfig dynamicConfig = buildPipelineFromPlan(plan.taskPlan());
            plan = PlanResult.pipeline(dynamicConfig, dynamicConfig.getSteps());
            log.info("[Orchestrator] converted TaskPlan to dynamic pipeline: {} steps={}",
                    dynamicConfig.getPipelineId(), dynamicConfig.getSteps().size());
        }

        // 将可映射到 Pipeline 的 SIMPLE 意图升级为 PIPELINE 执行
        if (plan.isSimple() && plan.intent() != null) {
            PipelineConfig upgradedConfig = upgradeToPipeline(plan.intent());
            if (upgradedConfig != null) {
                plan = PlanResult.pipeline(upgradedConfig, upgradedConfig.getSteps());
                log.info("[Orchestrator] upgraded intent {} to pipeline {}",
                        plan.intent(), upgradedConfig.getPipelineId());
            }
        }

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
     * 仅做意图识别，不执行 Pipeline（用于异步模式的第一步）
     */
    public PlanResult planOnly(String userId, String message) {
        return planOnly(userId, message, null, false);
    }

    /**
     * 仅做意图识别，不执行 Pipeline（用于异步模式的第一步，带对话历史）
     */
    public PlanResult planOnly(String userId, String message, String chatHistory) {
        return planOnly(userId, message, chatHistory, false);
    }

    /**
     * 仅做意图识别；skipGateG1 用于 Clarify 合并后跳过「无画像必追问」规则
     */
    public PlanResult planOnly(String userId, String message, String chatHistory, boolean skipGateG1) {
        log.info("[Orchestrator] planOnly: userId={}, msg={}", userId, message);
        Long uid = parseUserId(userId);
        LearnerContextDto learnerContext = uid != null
                ? learnerContextService.buildForUser(uid)
                : LearnerContextDto.builder().build();
        String learnerPrompt = learnerContext.getPromptBlock();
        PlanResult plan = planningAgent.decompose(message, userId, chatHistory, learnerPrompt);

        // 阶段二新增：将动态 TaskPlan 转为 PipelineConfig
        if (plan.isPlan() && plan.taskPlan() != null) {
            PipelineConfig dynamicConfig = buildPipelineFromPlan(plan.taskPlan());
            plan = PlanResult.pipeline(dynamicConfig, dynamicConfig.getSteps());
        }

        // 将可映射到 Pipeline 的 SIMPLE 意图升级为 PIPELINE
        if (plan.isSimple() && plan.intent() != null) {
            PipelineConfig upgradedConfig = upgradeToPipeline(plan.intent());
            if (upgradedConfig != null) {
                plan = PlanResult.pipeline(upgradedConfig, upgradedConfig.getSteps());
            }
        }

        plan = planningGateService.apply(plan, message, learnerContext, skipGateG1);
        return plan;
    }

    /**
     * 异步执行 Pipeline，每个步骤完成后通过 callback 通知
     * 返回 PipelineResult（同步等待完成，但在新线程中调用此方法）
     */
    public PipelineResult handlePipelineAsync(PlanResult plan, String userId,
                                              String message,
                                              PipelineEngine.StepCallback callback) {
        return handlePipelineAsync(plan, userId, message, callback, null);
    }

    public PipelineResult handlePipelineAsync(PlanResult plan, String userId,
                                              String message,
                                              PipelineEngine.StepCallback callback,
                                              java.util.function.Consumer<TaskContext> contextSetup) {
        PipelineConfig config = plan.pipelineConfig();
        String taskId = UUID.randomUUID().toString();
        TaskContext context = new TaskContext(taskId, userId, null, message);
        if (contextSetup != null) {
            contextSetup.accept(context);
        }
        supervisorService.run(plan, context);

        log.info("[Orchestrator] async pipeline: {}, steps={}",
                config.getName(), config.getSteps().size());

        try {
            return pipelineEngine.execute(config, context, callback);
        } catch (Exception e) {
            log.error("[Orchestrator] async pipeline failed: {}", e.getMessage(), e);
            return PipelineResult.failure(e.getMessage(), 0);
        }
    }

    /**
     * 完整消息处理（用于 WebSocket 场景，含结果推送）
     */
    public void handleMessage(String userId, String sessionId, String message,
                              WebSocketSession session,
                              java.util.function.Consumer<Map<String, Object>> eventCallback) {
        log.info("[Orchestrator] handleMessage: userId={}, msg={}", userId, message);

        Long uid = parseUserId(userId);
        String learnerContext = uid != null ? learnerContextService.buildPromptBlock(uid) : null;
        PlanResult plan = planningAgent.decompose(message, userId, null, learnerContext);

        // 阶段二新增：将动态 TaskPlan 转为 PipelineConfig
        if (plan.isPlan() && plan.taskPlan() != null) {
            PipelineConfig dynamicConfig = buildPipelineFromPlan(plan.taskPlan());
            plan = PlanResult.pipeline(dynamicConfig, dynamicConfig.getSteps());
        }

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

    public Map<String, Object> handleSimpleRequest(PlanIntent intent, String message) {
        return switch (intent) {
            case GREETING -> Map.of("route", "direct", "response",
                    "你好！我是 LQRAgent 智能学习助手，可以帮你解答问题、规划学习路径、生成学习资源。请问有什么可以帮助你的？");
            case HELP -> Map.of("route", "direct", "response", agentCardRegistry.buildHelpMessage());
            case LEARNING_PATH -> Map.of("route", "learning_path", "agent",
                    agentCardRegistry.matchBestAgent("learning_path"));
            case RESOURCE -> Map.of("route", "resource", "agent",
                    agentCardRegistry.matchBestAgent("resource"));
            case QUIZ -> Map.of("route", "resource", "agent",
                    agentCardRegistry.matchBestAgent("quiz"));
            case DIAGRAM -> Map.of("route", "diagram", "agent", AgentIds.DIAGRAM);
            case SUMMARY -> Map.of("route", "summary", "agent", AgentIds.SUMMARY);
            case RECOMMENDATION -> Map.of("route", "recommendation", "agent",
                    agentCardRegistry.matchBestAgent("recommendation"));
            case ASSESSMENT -> Map.of("route", "assessment", "agent",
                    agentCardRegistry.matchBestAgent("assessment"));
            case PROFILE -> Map.of("route", "profile", "agent", AgentIds.PROFILE, "message", message);
            case INTERVENTION -> Map.of("route", "intervention", "agent",
                    agentCardRegistry.matchBestAgent("intervention"));
            default -> Map.of("route", "qa", "agent", AgentIds.QA, "message", message);
        };
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

        try {
            TaskContext context = new TaskContext(taskId, userId, null, message);
            PipelineResult result = pipelineEngine.execute(config, context);

            if (result.isSuccess()) {
                String aggregated = aggregateResults(config, result);
                List<Artifact> artifacts = ArtifactExtractor.collectFromPipeline(result);
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("route", "pipeline_complete");
                response.put("agent", "pipeline_engine");
                response.put("response", aggregated);
                response.put("artifacts", artifacts);
                response.put("stepResults", result.getStepResults() != null
                        ? result.getStepResults().stream()
                            .map(sr -> Map.of(
                                    "stepId", sr.getStepId(),
                                    "agentId", sr.getAgentId(),
                                    "success", sr.isSuccess()))
                            .toList()
                        : List.of());
                response.put("durationMs", result.getTotalDurationMs());
                return response;
            } else {
                return Map.of(
                        "route", "pipeline_error",
                        "agent", "pipeline_engine",
                        "response", "任务执行失败: " + result.getErrorMessage(),
                        "error", result.getErrorMessage()
                );
            }
        } catch (Exception e) {
            log.error("[Orchestrator] pipeline execution failed: {}", e.getMessage(), e);
            return Map.of(
                    "route", "pipeline_error",
                    "agent", "pipeline_engine",
                    "response", "任务执行失败: " + e.getMessage(),
                    "error", e.getMessage()
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

        // 持久化任务记录
        final Long uid = parseUserId(userId);
        if (uid != null) {
            pipelineTaskService.createTask(taskId, uid, config.getPipelineId(),
                    config.getName(), message, config.getSteps().size(), config);
        }

        // 推送进度：准备开始
        eventCallback.accept(Map.of(
                "type", "pipeline_start",
                "pipelineName", config.getName(),
                "stepCount", config.getSteps().size(),
                "taskId", taskId,
                "steps", config.getSteps().stream()
                        .map(s -> Map.of("stepId", s.getStepId(), "agentId", s.getAgentId(), "action", s.getAction()))
                        .toList()
        ));

        TaskContext context = new TaskContext(taskId, userId, sessionId, message);
        if (uid != null) {
            learnerContextService.enrichTaskContext(context, uid);
        }

        // 在后台线程执行 Pipeline，不阻塞 WebSocket 线程
        Thread executor = new Thread(() -> {
            try {
                // 使用带回调的 execute，每步完成时更新 DB + 推送事件
                PipelineResult result = pipelineEngine.execute(config, context, (stepId, agentId, success, data) -> {
                    // 1. 持久化步骤结果
                    if (uid != null) {
                        pipelineTaskService.updateCurrentStep(taskId, stepId, stepId);
                        pipelineTaskService.recordStepResult(taskId, stepId, agentId, success, data, 0);
                    }
                    // 2. 推送 WebSocket 进度事件
                    eventCallback.accept(Map.of(
                            "type", "agent_step",
                            "stepId", stepId,
                            "agentId", agentId,
                            "success", success,
                            "taskId", taskId
                    ));
                });

                if (result.isSuccess()) {
                    if (uid != null) {
                        pipelineTaskService.markCompleted(taskId);
                    }
                    String aggregated = aggregateResults(config, result);
                    eventCallback.accept(Map.of(
                            "type", "pipeline_complete",
                            "pipelineName", config.getName(),
                            "response", aggregated,
                            "artifacts", ArtifactExtractor.collectFromPipeline(result),
                            "route", "pipeline_complete",
                            "durationMs", result.getTotalDurationMs()
                    ));
                } else {
                    if (uid != null) {
                        pipelineTaskService.markFailed(taskId, result.getErrorMessage(),
                                findFailedStepId(result));
                    }
                    eventCallback.accept(Map.of(
                            "type", "pipeline_error",
                            "error", result.getErrorMessage()
                    ));
                }
            } catch (Exception e) {
                log.error("[Orchestrator] pipeline execution failed: {}", e.getMessage(), e);
                if (uid != null) {
                    pipelineTaskService.markFailed(taskId, e.getMessage(), null);
                }
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
    public String aggregateResults(PipelineConfig config, PipelineResult result) {
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
        List<Artifact> artifacts = ArtifactExtractor.fromStepData(agentId, data);
        if (!StepStreamPolicy.shouldStreamContent(agentId, data, artifacts)) {
            return summarizeFromArtifacts(artifacts);
        }

        if (data.containsKey("summary")) {
            return String.valueOf(data.get("summary"));
        }
        if (data.containsKey("result")) {
            return String.valueOf(data.get("result"));
        }
        if (data.containsKey("llm_analysis")) {
            return String.valueOf(data.get("llm_analysis"));
        }
        if (data.containsKey("content")) {
            Object content = data.get("content");
            if (content != null && !String.valueOf(content).isBlank()) {
                return String.valueOf(content);
            }
        }
        if (AgentIds.LEARNING_PATH.equals(agentId) && data.containsKey("nodes")) {
            return "学习路径已生成，包含 " + data.get("nodeCount") + " 个节点。";
        }
        String fromArtifacts = summarizeFromArtifacts(artifacts);
        if (fromArtifacts != null) {
            return fromArtifacts;
        }
        return "步骤完成 (" + agentId + ")";
    }

    private String summarizeFromArtifacts(List<Artifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return null;
        }
        for (Artifact artifact : artifacts) {
            Map<String, Object> payload = artifact.getPayload();
            if (payload == null) {
                continue;
            }
            if (artifact.getKind() == ArtifactKind.IMAGE && payload.get("url") != null) {
                return "![生成图片](" + payload.get("url") + ")";
            }
            if (artifact.getKind() == ArtifactKind.VIDEO && payload.get("url") != null) {
                return "视频已生成: " + payload.get("url");
            }
            if (artifact.getKind() == ArtifactKind.LEARNING_PATH) {
                Object nodeCount = payload.get("nodeCount");
                if (nodeCount != null) {
                    return "学习路径已生成，包含 " + nodeCount + " 个节点。";
                }
                Object nodes = payload.get("nodes");
                if (nodes instanceof List<?> list) {
                    return "学习路径已生成，包含 " + list.size() + " 个节点。";
                }
            }
        }
        return null;
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

        // 使用 ExecutorService + Future 实现优雅超时控制
        ExecutorService executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "learn-exec-" + taskId);
            t.setDaemon(true);
            return t;
        });
        
        Future<?> future = executorService.submit(() -> {
            try {
                // 发送开始消息
                sendToFrontend(session, Map.of(
                        "type", "progress",
                        "agent", "orchestrator",
                        "message", "开始生成学习路径..."
                ));
                
                PipelineResult result = pipelineEngine.execute(config, context);
                if (result.isSuccess()) {
                    // 从数据库获取刚生成的路径数据（Pipeline 中 GeneratePathTool 已保存到 DB）
                    Long uid = Long.parseLong(userId);
                    java.util.Optional<LearningPathDto> pathOpt = learningPathService.getCurrentPath(uid);
                    
                    Map<String, Object> agentResult = new HashMap<>();
                    if (pathOpt.isPresent()) {
                        LearningPathDto pathDto = pathOpt.get();
                        Map<String, Object> pathData = new HashMap<>();
                        pathData.put("goal", pathDto.getGoal());
                        pathData.put("planDescription", pathDto.getPlanDescription());
                        pathData.put("nodes", pathDto.getNodes());
                        agentResult.put("path", pathData);
                        agentResult.put("result", pathData);
                        log.info("[Orchestrator] path data fetched from DB: nodes={}", pathDto.getNodes().size());
                    } else {
                        log.warn("[Orchestrator] pipeline succeeded but no path found in DB");
                        agentResult.put("path", Map.of("goal", goal, "nodes", List.of(), "planDescription", ""));
                        agentResult.put("result", agentResult.get("path"));
                    }
                    
                    Map<String, Object> results = new HashMap<>();
                    results.put("learning_path_agent", agentResult);
                    
                    sendToFrontend(session, Map.of(
                            "type", "complete",
                            "taskId", taskId,
                            "results", results
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
                String errorMsg = e.getCause() instanceof InterruptedException 
                    ? "生成超时，请稍后重试" 
                    : "Pipeline执行失败: " + e.getMessage();
                sendToFrontend(session, Map.of(
                        "type", "error",
                        "taskId", taskId,
                        "message", errorMsg
                ));
            } finally {
                tasks.remove(taskId);
            }
        });
        
        // 设置整体超时（3分钟）
        Thread timeoutThread = new Thread(() -> {
            try {
                // 等待任务完成，最多3分钟
                future.get(180, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("[Orchestrator] learn task {} timed out after 3 minutes", taskId);
                sendToFrontend(session, Map.of(
                        "type", "error",
                        "taskId", taskId,
                        "message", "生成超时，请稍后重试"
                ));
                tasks.remove(taskId);
                future.cancel(true); // 请求取消任务
            } catch (Exception e) {
                log.error("[Orchestrator] learn task {} execution error: {}", taskId, e.getMessage());
            } finally {
                executorService.shutdownNow();
            }
        }, "learn-timeout-" + taskId);
        timeoutThread.setDaemon(true);
        timeoutThread.start();

        return taskId;
    }

    // ==================== 工具方法 ====================

    /**
     * 阶段二新增：从 TaskPlan 动态构建 PipelineConfig
     * <p>
     * LLM 输出的 TaskStep 序列 → PipelineStep 序列，由 PipelineEngine 执行
     */
    public PipelineConfig buildPipelineFromPlan(TaskPlan plan) {
        List<com.lqragent.backend.orchestrator.pipeline.PipelineStep> pipelineSteps = new java.util.ArrayList<>();
        for (TaskStep ts : plan.steps()) {
            pipelineSteps.add(com.lqragent.backend.orchestrator.pipeline.PipelineStep.builder()
                    .stepId(ts.getStepId())
                    .agentId(ts.getAgentId())
                    .action(ts.getAction())
                    .params(ts.getParams() != null ? new HashMap<>(ts.getParams()) : new HashMap<>())
                    .dependsOn(ts.getDependsOn() != null ? ts.getDependsOn() : List.of())
                    .resultMapping(ts.getInputFromSteps() != null ? ts.getInputFromSteps() : Map.of())
                    .maxRetries(ts.getMaxRetries())
                    .optional(ts.isOptional())
                    .timeoutMs(60000)
                    .build());
        }
        return PipelineConfig.builder()
                .pipelineId("dynamic_" + plan.planId())
                .name("动态任务: " + (plan.goal() != null ? plan.goal() : ""))
                .description("LLM 规划的动态 Pipeline")
                .steps(pipelineSteps)
                .totalTimeoutMs(180000)
                .parallel(false)
                .build();
    }

    /**
     * 将 SIMPLE 意图升级为 Pipeline（当存在对应模板时）
     */
    private PipelineConfig upgradeToPipeline(PlanIntent intent) {
        return switch (intent) {
            case LEARNING_PATH -> PipelineTemplates.learningPath();
            case DIAGRAM -> PipelineTemplates.diagram();
            case SUMMARY -> PipelineTemplates.summary();
            case QUIZ -> PipelineTemplates.quiz();
            case RECOMMENDATION -> PipelineTemplates.recommendation();
            case ASSESSMENT -> PipelineTemplates.assessment();
            case RESOURCE -> PipelineTemplates.resource();
            case INTERVENTION -> PipelineTemplates.intervention();
            default -> null;
        };
    }

    private String findFailedStepId(PipelineResult result) {
        if (result.getStepResults() == null) {
            return null;
        }
        return result.getStepResults().stream()
                .filter(r -> !r.isSuccess())
                .map(StepResult::getStepId)
                .findFirst()
                .orElse(null);
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

    private static Long parseUserId(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try { return Long.parseLong(userId); } catch (NumberFormatException e) { return null; }
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