package com.lqragent.backend.orchestrator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.lqragent.backend.orchestrator.capability.AgentCapability;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.pipeline.service.PipelineTaskService;
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
    private final LearningPathService learningPathService;
    private final PipelineTaskService pipelineTaskService;
    private final ObjectMapper mapper = new ObjectMapper();

    // 任务状态跟踪（用于 learn 流程的 WebSocket 推送）
    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    public OrchestratorCore(RedisStreamsService streams, AgentRunLogService runLogService,
                           LlmClient llmClient, AgentMemory agentMemory,
                           PlanningAgent planningAgent, PipelineEngine pipelineEngine,
                           CapabilityRegistry capabilityRegistry,
                           LearningPathService learningPathService,
                           PipelineTaskService pipelineTaskService) {
        this.streams = streams;
        this.runLogService = runLogService;
        this.llmClient = llmClient;
        this.agentMemory = agentMemory;
        this.planningAgent = planningAgent;
        this.pipelineEngine = pipelineEngine;
        this.capabilityRegistry = capabilityRegistry;
        this.learningPathService = learningPathService;
        this.pipelineTaskService = pipelineTaskService;
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

        // 批量注册所有 Agent 的能力描述（供帮助信息和能力发现使用）
        registerAgentCapabilities();

        log.info("[OrchestratorCore] registered orchestrator and {} agent capabilities",
                capabilityRegistry.size());
    }

    private void registerAgentCapabilities() {
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.QA)
                .displayName("智能问答")
                .description("智能问答：解答学习问题、知识检索、概念解释")
                .actions(List.of("search_knowledge", "generate_answer"))
                .tags(Set.of("qa", "question", "answer"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.LEARNING_PATH)
                .displayName("学习路径规划")
                .description("学习路径规划：生成个性化学习路线和知识图谱")
                .actions(List.of("generate_path"))
                .tags(Set.of("learning_path", "path", "plan"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.RESOURCE)
                .displayName("学习资源生成")
                .description("学习资源生成：生成讲义、练习题、代码示例等")
                .actions(List.of("generate_lesson", "batch_generate"))
                .tags(Set.of("resource", "lesson", "quiz"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.PROFILE)
                .displayName("用户画像")
                .description("用户画像分析：了解你的学习状态和知识掌握程度")
                .actions(List.of("get_profile"))
                .tags(Set.of("profile", "learner"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.DIAGRAM)
                .displayName("图表生成")
                .description("图表生成：生成思维导图、流程图、知识图谱等")
                .actions(List.of("generate_diagram"))
                .tags(Set.of("diagram", "mindmap", "flowchart"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.SUMMARY)
                .displayName("总结生成")
                .description("总结生成：生成知识点摘要和复习笔记")
                .actions(List.of("generate_summary"))
                .tags(Set.of("summary", "review"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.QUIZ)
                .displayName("题目生成")
                .description("题目生成：按要求或基于知识库资料生成混合题型练习题")
                .actions(List.of("generate_quiz"))
                .tags(Set.of("quiz", "question", "exercise", "test"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.RECOMMENDATION)
                .displayName("个性化推荐")
                .description("个性化推荐：根据学习情况推荐资源和方向")
                .actions(List.of("recommend"))
                .tags(Set.of("recommendation", "suggest"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.ASSESSMENT)
                .displayName("学习评估")
                .description("学习评估：评估学习效果、批改作业")
                .actions(List.of("grade"))
                .tags(Set.of("assessment", "grade", "evaluation"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.INTERVENTION)
                .displayName("学习干预")
                .description("学习干预：分析学习状态并提供干预建议")
                .actions(List.of("assess_and_intervene"))
                .tags(Set.of("intervention", "suggest"))
                .build());
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.MEDIA_GEN)
                .displayName("媒体生成")
                .description("媒体生成：生成图片、视频等多媒体内容")
                .actions(List.of("generate_media"))
                .tags(Set.of("media", "image", "video"))
                .build());
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
        return planOnly(userId, message, null);
    }

    /**
     * 仅做意图识别，不执行 Pipeline（用于异步模式的第一步，带对话历史）
     */
    public PlanResult planOnly(String userId, String message, String chatHistory) {
        log.info("[Orchestrator] planOnly: userId={}, msg={}", userId, message);
        PlanResult plan = planningAgent.decompose(message, userId, chatHistory);

        // 将可映射到 Pipeline 的 SIMPLE 意图升级为 PIPELINE
        if (plan.isSimple() && plan.intent() != null) {
            PipelineConfig upgradedConfig = upgradeToPipeline(plan.intent());
            if (upgradedConfig != null) {
                plan = PlanResult.pipeline(upgradedConfig, upgradedConfig.getSteps());
            }
        }
        return plan;
    }

    /**
     * 异步执行 Pipeline，每个步骤完成后通过 callback 通知
     * 返回 PipelineResult（同步等待完成，但在新线程中调用此方法）
     */
    public PipelineResult handlePipelineAsync(PlanResult plan, String userId,
                                              String message,
                                              PipelineEngine.StepCallback callback) {
        PipelineConfig config = plan.pipelineConfig();
        String taskId = UUID.randomUUID().toString();
        TaskContext context = new TaskContext(taskId, userId, null, message);

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

    public Map<String, Object> handleSimpleRequest(PlanIntent intent, String message) {
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
            case INTERVENTION -> Map.of("route", "intervention", "agent",
                    capabilityRegistry.matchBestAgent("intervention"));
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

        try {
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
                    config.getName(), message, config.getSteps().size());
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
                            "route", "pipeline_complete",
                            "durationMs", result.getTotalDurationMs()
                    ));
                } else {
                    if (uid != null) {
                        pipelineTaskService.markFailed(taskId, result.getErrorMessage());
                    }
                    eventCallback.accept(Map.of(
                            "type", "pipeline_error",
                            "error", result.getErrorMessage()
                    ));
                }
            } catch (Exception e) {
                log.error("[Orchestrator] pipeline execution failed: {}", e.getMessage(), e);
                if (uid != null) {
                    pipelineTaskService.markFailed(taskId, e.getMessage());
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
        // content 字段（Agent 的 LLM 回答）
        if (data.containsKey("content")) {
            Object content = data.get("content");
            if (content != null && !String.valueOf(content).isBlank()) {
                return String.valueOf(content);
            }
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