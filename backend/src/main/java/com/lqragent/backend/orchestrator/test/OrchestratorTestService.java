package com.lqragent.backend.orchestrator.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.lqragent.backend.agents.aiserver.tools.AiServerToolFactory;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.orchestrator.card.AgentCardRegistry;
import com.lqragent.backend.orchestrator.context.LearnerContextDto;
import com.lqragent.backend.orchestrator.context.LearnerContextService;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineStep;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.pipeline.entity.PipelineTask;
import com.lqragent.backend.orchestrator.pipeline.service.PipelineRetryService;
import com.lqragent.backend.orchestrator.pipeline.service.PipelineTaskService;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AgentCardDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AgentRunTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AiServerToolDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.CapabilityTestResult;
import com.lqragent.backend.orchestrator.test.intent.IntentSuiteRunner;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentSuiteResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.LearningLoopTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PipelineTaskStatusDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PipelineTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PlanTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.StepResultDto;
import com.lqragent.backend.quiz.service.QuizService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrator 测试服务 — 供 REST /api/test、控制台、管理后台 agent-test 共用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorTestService {

    private final OrchestratorCore orchestratorCore;
    private final PipelineEngine pipelineEngine;
    private final AgentCardRegistry agentCardRegistry;
    private final AiServerWsProxy aiServerWsProxy;
    private final AiServerToolFactory aiServerToolFactory;
    private final PipelineTaskService pipelineTaskService;
    private final PipelineRetryService pipelineRetryService;
    private final QuizService quizService;
    private final IntentSuiteRunner intentSuiteRunner;
    private final LearnerContextService learnerContextService;

    /**
     * 仅意图规划（不执行 Pipeline）
     */
    public PlanTestResult planOnly(String userId, String message, String chatHistory) {
        long start = System.currentTimeMillis();
        try {
            return resolvePlan(userId, message, chatHistory).dto();
        } catch (Exception e) {
            log.error("[OrchestratorTest] planOnly failed: {}", e.getMessage(), e);
            return new PlanTestResult(false, null, null, null, null, message,
                    List.of(), List.of(), null, null, null,
                    System.currentTimeMillis() - start, e.getMessage());
        }
    }

    /**
     * 同步执行完整链路：plan → pipeline（若需要）
     */
    public PipelineTestResult runPipelineSync(String userId, String message) {
        long start = System.currentTimeMillis();
        try {
            ResolvedPlan resolved = resolvePlan(userId, message, null);
            PlanTestResult planInfo = resolved.dto();

            if ("SIMPLE".equals(planInfo.planType()) || "CLARIFY".equals(planInfo.planType())) {
                return new PipelineTestResult(
                        true,
                        planInfo,
                        List.of(),
                        List.of(),
                        System.currentTimeMillis() - start,
                        null
                );
            }

            return executePipeline(userId, message, resolved.plan(), planInfo, start);
        } catch (Exception e) {
            log.error("[OrchestratorTest] runPipelineSync failed: {}", e.getMessage(), e);
            PlanTestResult failedPlan = new PlanTestResult(false, null, null, null, null, message,
                    List.of(), List.of(), null, null, null,
                    System.currentTimeMillis() - start, e.getMessage());
            return new PipelineTestResult(false, failedPlan, List.of(), List.of(),
                    System.currentTimeMillis() - start, e.getMessage());
        }
    }

    /**
     * 完整 Agent 路由测试（兼容旧 /api/test/agent）
     */
    public AgentRunTestResult runAgentRoute(String userId, String message) {
        long start = System.currentTimeMillis();
        try {
            ResolvedPlan resolved = resolvePlan(userId, message, null);
            PlanTestResult planInfo = resolved.dto();
            PlanResult plan = resolved.plan();

            if ("PIPELINE".equals(planInfo.planType()) || "PLAN".equals(planInfo.planType())) {
                PipelineTestResult pipeline = executePipeline(userId, message, plan, planInfo, start);
                String response = pipeline.stepResults().isEmpty()
                        ? planInfo.response()
                        : pipeline.stepResults().stream()
                                .filter(StepResultDto::success)
                                .map(StepResultDto::summary)
                                .filter(s -> s != null && !s.isBlank())
                                .reduce((a, b) -> b)
                                .orElse("");

                return new AgentRunTestResult(
                        pipeline.success(),
                        pipeline.success() ? "pipeline_complete" : "pipeline_error",
                        "pipeline_engine",
                        OrchestratorTestSupport.truncate(response, 500),
                        planInfo,
                        pipeline,
                        System.currentTimeMillis() - start,
                        pipeline.error()
                );
            }

            return new AgentRunTestResult(
                    planInfo.success(),
                    planInfo.route(),
                    planInfo.intent() != null ? planInfo.intent().toLowerCase() : "orchestrator",
                    planInfo.response(),
                    planInfo,
                    null,
                    System.currentTimeMillis() - start,
                    planInfo.error()
            );
        } catch (Exception e) {
            log.error("[OrchestratorTest] runAgentRoute failed: {}", e.getMessage(), e);
            PlanTestResult failedPlan = new PlanTestResult(false, null, null, null, null, message,
                    List.of(), List.of(), null, null, null,
                    System.currentTimeMillis() - start, e.getMessage());
            return new AgentRunTestResult(
                    false, "error", "orchestrator", null,
                    failedPlan, null,
                    System.currentTimeMillis() - start, e.getMessage()
            );
        }
    }

    public List<AgentCardDto> listAgentCards() {
        return agentCardRegistry.getAll().stream()
                .map(OrchestratorTestSupport::toAgentCardDto)
                .sorted((a, b) -> a.agentId().compareTo(b.agentId()))
                .toList();
    }

    public List<AiServerToolDto> listAiServerTools() {
        List<AiServerToolDto> tools = new ArrayList<>();
        for (AgentTool t : List.of(
                aiServerToolFactory.deepSolveTool(),
                aiServerToolFactory.deepQuestionTool(),
                aiServerToolFactory.visualizeTool())) {
            tools.add(new AiServerToolDto(t.name(), t.description()));
        }
        return tools;
    }

    public CapabilityTestResult testCapability(String capability, Map<String, Object> args) {
        long start = System.currentTimeMillis();
        try {
            String result = aiServerWsProxy.callCapability(capability, args != null ? args : Map.of());
            boolean ok = result != null && !result.isBlank();
            return new CapabilityTestResult(
                    ok,
                    capability,
                    ok ? OrchestratorTestSupport.truncate(result, 800) : null,
                    System.currentTimeMillis() - start,
                    ok ? null : "ai-server 返回空"
            );
        } catch (Exception e) {
            return new CapabilityTestResult(
                    false, capability, null,
                    System.currentTimeMillis() - start,
                    e.getMessage()
            );
        }
    }

    /**
     * 同步执行 learning_loop Pipeline（复用 QuizService 线上逻辑）
     */
    public LearningLoopTestResult runLearningLoop(Long userId, Long questionId,
                                                   String answer, boolean correct, int score) {
        long start = System.currentTimeMillis();
        try {
            PipelineResult result = quizService.executeLearningLoopForQuestion(
                    userId, questionId, answer, correct, score, null, false);
            return toLearningLoopTestResult(result, start);
        } catch (Exception e) {
            log.error("[OrchestratorTest] learning loop failed: {}", e.getMessage(), e);
            return new LearningLoopTestResult(false, List.of(),
                    QuizService.LEARNING_LOOP_STEP_IDS, false,
                    System.currentTimeMillis() - start, e.getMessage());
        }
    }

    private LearningLoopTestResult toLearningLoopTestResult(PipelineResult result, long startMs) {
        List<StepResultDto> steps = mapStepResults(result.getStepResults());
        boolean stepsAligned = isLearningLoopStepsAligned(result.getStepResults());
        boolean success = result.isSuccess() && stepsAligned;
        String error = null;
        if (!result.isSuccess()) {
            error = result.getErrorMessage();
        } else if (!stepsAligned) {
            error = "步骤序列不符合 learning_loop 约定: 期望 "
                    + QuizService.LEARNING_LOOP_STEP_IDS
                    + "，实际 " + steps.stream().map(StepResultDto::stepId).toList();
        }
        return new LearningLoopTestResult(
                success,
                steps,
                QuizService.LEARNING_LOOP_STEP_IDS,
                stepsAligned,
                System.currentTimeMillis() - startMs,
                error
        );
    }

    private boolean isLearningLoopStepsAligned(List<StepResult> stepResults) {
        if (stepResults == null || stepResults.size() != QuizService.LEARNING_LOOP_STEP_IDS.size()) {
            return false;
        }
        for (int i = 0; i < QuizService.LEARNING_LOOP_STEP_IDS.size(); i++) {
            if (!QuizService.LEARNING_LOOP_STEP_IDS.get(i).equals(stepResults.get(i).getStepId())) {
                return false;
            }
        }
        return stepResults.stream().allMatch(StepResult::isSuccess);
    }

    public IntentSuiteResult runIntentSuite(String userId) {
        return intentSuiteRunner.run(userId, orchestratorCore::planOnly);
    }

    public LearnerContextDto getLearnerContext(Long userId) {
        return learnerContextService.buildForUser(userId);
    }

    public Optional<PipelineTaskStatusDto> getPipelineTaskStatus(String taskId) {
        return pipelineTaskService.findByTaskId(taskId).map(this::toTaskStatus);
    }

    public Optional<PipelineTaskStatusDto> getLatestPipelineTask(Long userId) {
        return pipelineTaskService.findLatestByUserId(userId).map(this::toTaskStatus);
    }

    /** 从 failed_step 断点重试（sync=true 时等待完成，供控制台使用） */
    public Optional<PipelineTaskStatusDto> retryPipelineTask(String taskId, Long userId, boolean sync) {
        var result = sync
                ? pipelineRetryService.retrySync(taskId, userId, 180_000)
                : pipelineRetryService.retryAsync(taskId, userId);
        return result.flatMap(d -> pipelineTaskService.findByTaskId(d.getTaskId()).map(this::toTaskStatus));
    }

    /**
     * 构造一条 FAILED 任务（step1 已成功，step2 失败），供断点重试测试
     */
    public Optional<PipelineTaskStatusDto> seedFailedPipelineTask(Long userId) {
        String taskId = UUID.randomUUID().toString();
        PipelineConfig config = PipelineConfig.builder()
                .pipelineId("test_retry_" + taskId.substring(0, 8))
                .name("断点重试测试")
                .parallel(false)
                .steps(List.of(
                        PipelineStep.builder()
                                .stepId("step1")
                                .agentId(AgentIds.QA)
                                .action("answer")
                                .build(),
                        PipelineStep.builder()
                                .stepId("step2")
                                .agentId(AgentIds.QA)
                                .action("answer")
                                .dependsOn(List.of("step1"))
                                .build()
                ))
                .build();

        pipelineTaskService.createTask(taskId, userId, config.getPipelineId(),
                config.getName(), "测试断点重试：什么是闭包", config.getSteps().size(), config);
        pipelineTaskService.recordStepResult(taskId, "step1", AgentIds.QA, true,
                Map.of("success", true, "content", "闭包是函数与其词法环境的组合"), 120);
        pipelineTaskService.markFailed(taskId, "step2 simulated failure for retry test", "step2");
        return pipelineTaskService.findByTaskId(taskId).map(this::toTaskStatus);
    }

    // ==================== internal ====================

    private record ResolvedPlan(PlanResult plan, PlanTestResult dto) {}

    /** 单次 LLM 规划，返回原始 PlanResult 与展示用 DTO */
    private ResolvedPlan resolvePlan(String userId, String message, String chatHistory) {
        long planStart = System.currentTimeMillis();
        PlanResult plan = orchestratorCore.planOnly(userId, message, chatHistory);
        PlanTestResult dto = buildPlanTestResult(plan, message, System.currentTimeMillis() - planStart);
        return new ResolvedPlan(plan, dto);
    }

    private PlanTestResult buildPlanTestResult(PlanResult plan, String message, long durationMs) {
        PlanTestResult base = OrchestratorTestSupport.fromPlanResult(plan, message, durationMs);

        if (plan.isSimple() && plan.intent() != null) {
            Map<String, Object> simple = orchestratorCore.handleSimpleRequest(plan.intent(), message);
            return OrchestratorTestSupport.withRoute(base,
                    String.valueOf(simple.getOrDefault("route", "direct")),
                    String.valueOf(simple.getOrDefault("response", simple.getOrDefault("message", ""))));
        }
        if (plan.isClarify()) {
            return OrchestratorTestSupport.withRoute(base, "clarify",
                    plan.clarifyQuestions() != null ? String.join("；", plan.clarifyQuestions()) : "");
        }
        if (OrchestratorTestSupport.isPipelinePlan(plan)) {
            return OrchestratorTestSupport.withRoute(base, "pipeline", "（规划完成，使用 pipeline 命令执行）");
        }
        return base;
    }

    /** 用已解析的 PlanResult 执行 Pipeline，不再重复规划 */
    private PipelineTestResult executePipeline(String userId, String message, PlanResult plan,
                                               PlanTestResult planInfo, long startMs) {
        if (!OrchestratorTestSupport.isPipelinePlan(plan)) {
            String error = planInfo.steps() == null || planInfo.steps().isEmpty()
                    ? "无 Pipeline 步骤可执行"
                    : "规划结果不是 Pipeline";
            return new PipelineTestResult(false, planInfo, List.of(), List.of(),
                    System.currentTimeMillis() - startMs, error);
        }

        try {
            PipelineResult result = orchestratorCore.handlePipelineAsync(
                    plan, userId, message, null,
                    ctx -> ctx.put("chat.sessionId", "test-pipeline-" + userId));
            List<StepResultDto> stepDtos = mapStepResults(result.getStepResults());
            List<com.lqragent.backend.orchestrator.artifact.Artifact> artifacts =
                    OrchestratorTestSupport.collectAllArtifacts(stepDtos);

            return new PipelineTestResult(
                    result.isSuccess(),
                    planInfo,
                    stepDtos,
                    artifacts,
                    System.currentTimeMillis() - startMs,
                    result.isSuccess() ? null : result.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("[OrchestratorTest] executePipeline failed: {}", e.getMessage(), e);
            return new PipelineTestResult(false, planInfo, List.of(), List.of(),
                    System.currentTimeMillis() - startMs, e.getMessage());
        }
    }

    private List<StepResultDto> mapStepResults(List<StepResult> stepResults) {
        if (stepResults == null) return List.of();
        return stepResults.stream().map(OrchestratorTestSupport::toStepResultDto).toList();
    }

    private PipelineTaskStatusDto toTaskStatus(PipelineTask task) {
        return new PipelineTaskStatusDto(
                task.getTaskId(),
                task.getStatus(),
                task.getPipelineName(),
                task.getGoal(),
                task.getStepCount() != null ? task.getStepCount() : 0,
                task.getCompletedSteps() != null ? task.getCompletedSteps() : 0,
                task.getCurrentStep(),
                task.getFailedStep(),
                task.getErrorMessage()
        );
    }
}
