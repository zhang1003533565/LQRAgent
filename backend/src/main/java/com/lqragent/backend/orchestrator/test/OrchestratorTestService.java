package com.lqragent.backend.orchestrator.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.lqragent.backend.agents.aiserver.tools.AiServerToolFactory;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.orchestrator.card.AgentCardRegistry;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.pipeline.entity.PipelineTask;
import com.lqragent.backend.orchestrator.pipeline.service.PipelineTaskService;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AgentCardDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AgentRunTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AiServerToolDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.CapabilityTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentCaseResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentSuiteResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.LearningLoopTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PipelineTaskStatusDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PipelineTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PlanTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.StepResultDto;
import com.lqragent.backend.quiz.entity.QuestionBank;
import com.lqragent.backend.quiz.repository.QuestionBankRepository;

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
    private final QuestionBankRepository questionBankRepository;

    /**
     * 仅意图规划（不执行 Pipeline）
     */
    public PlanTestResult planOnly(String userId, String message, String chatHistory) {
        long start = System.currentTimeMillis();
        try {
            PlanResult plan = orchestratorCore.planOnly(userId, message, chatHistory);
            PlanTestResult base = OrchestratorTestSupport.fromPlanResult(plan, message, System.currentTimeMillis() - start);

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
        PlanTestResult planInfo = planOnly(userId, message, null);

        if (!planInfo.success()) {
            return new PipelineTestResult(false, planInfo, List.of(), List.of(),
                    System.currentTimeMillis() - start, planInfo.error());
        }

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

        if (planInfo.steps() == null || planInfo.steps().isEmpty()) {
            return new PipelineTestResult(false, planInfo, List.of(), List.of(),
                    System.currentTimeMillis() - start, "无 Pipeline 步骤可执行");
        }

        try {
            PlanResult plan = orchestratorCore.planOnly(userId, message, null);
            if (!OrchestratorTestSupport.isPipelinePlan(plan)) {
                return new PipelineTestResult(false, planInfo, List.of(), List.of(),
                        System.currentTimeMillis() - start, "规划结果不是 Pipeline");
            }

            PipelineResult result = orchestratorCore.handlePipelineAsync(plan, userId, message, null);
            List<StepResultDto> stepDtos = mapStepResults(result.getStepResults());
            List<com.lqragent.backend.orchestrator.artifact.Artifact> artifacts =
                    OrchestratorTestSupport.collectAllArtifacts(stepDtos);

            return new PipelineTestResult(
                    result.isSuccess(),
                    planInfo,
                    stepDtos,
                    artifacts,
                    System.currentTimeMillis() - start,
                    result.isSuccess() ? null : result.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("[OrchestratorTest] runPipelineSync failed: {}", e.getMessage(), e);
            return new PipelineTestResult(false, planInfo, List.of(), List.of(),
                    System.currentTimeMillis() - start, e.getMessage());
        }
    }

    /**
     * 完整 Agent 路由测试（兼容旧 /api/test/agent）
     */
    public AgentRunTestResult runAgentRoute(String userId, String message) {
        long start = System.currentTimeMillis();
        PlanTestResult planInfo = planOnly(userId, message, null);

        if ("PIPELINE".equals(planInfo.planType()) || "PLAN".equals(planInfo.planType())) {
            PipelineTestResult pipeline = runPipelineSync(userId, message);
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
     * 同步执行 learning_loop Pipeline（与 QuizService 线上逻辑一致）
     */
    public LearningLoopTestResult runLearningLoop(Long userId, Long questionId,
                                                   String answer, boolean correct, int score) {
        long start = System.currentTimeMillis();
        try {
            QuestionBank question = questionBankRepository.findById(questionId)
                    .orElseThrow(() -> new IllegalArgumentException("题目不存在: " + questionId));

            PipelineConfig config = PipelineTemplates.learningLoop();
            TaskContext context = new TaskContext(
                    "test-loop-" + System.currentTimeMillis(),
                    String.valueOf(userId),
                    "test-learning-loop",
                    "测试学习闭环"
            );

            Map<String, Object> quizSubmission = new LinkedHashMap<>();
            quizSubmission.put("userId", userId);
            quizSubmission.put("questionId", question.getId());
            quizSubmission.put("kpId", question.getKnowledgePoint());
            quizSubmission.put("question", question.getTitle());
            quizSubmission.put("questionType", question.getQuestionType());
            quizSubmission.put("answer", answer != null ? answer : "");
            quizSubmission.put("correctAnswer", question.getCorrectAnswer());
            quizSubmission.put("correct", correct);
            quizSubmission.put("score", score);
            quizSubmission.put("analysis", question.getAnalysis());
            context.setResult("quiz_submission", Map.of("answers", quizSubmission, "quiz", quizSubmission));

            PipelineResult result = pipelineEngine.execute(config, context);
            List<StepResultDto> steps = mapStepResults(result.getStepResults());

            return new LearningLoopTestResult(
                    result.isSuccess(),
                    steps,
                    System.currentTimeMillis() - start,
                    result.isSuccess() ? null : result.getErrorMessage()
            );
        } catch (Exception e) {
            log.error("[OrchestratorTest] learning loop failed: {}", e.getMessage(), e);
            return new LearningLoopTestResult(false, List.of(),
                    System.currentTimeMillis() - start, e.getMessage());
        }
    }

    public IntentSuiteResult runIntentSuite(String userId) {
        long start = System.currentTimeMillis();
        List<IntentCaseDefinition> cases = buildIntentCases();
        List<IntentCaseResult> results = new ArrayList<>();
        int passed = 0;

        for (IntentCaseDefinition c : cases) {
            PlanResult plan = orchestratorCore.planOnly(userId, c.input(), null);
            String actualType = plan.type().name();
            List<String> actualAgents = OrchestratorTestSupport.agentIdsFromPlan(plan);

            boolean typeOk = c.expectedPlanTypes().contains(actualType)
                    || (c.expectedPlanTypes().contains("PIPELINE")
                    && ("PLAN".equals(actualType) || "PIPELINE".equals(actualType)));

            boolean agentOk = true;
            if (c.expectedAgentIds() != null && !c.expectedAgentIds().isEmpty()) {
                agentOk = c.expectedAgentIds().stream().anyMatch(actualAgents::contains);
            }

            boolean casePassed = typeOk && agentOk;
            if (casePassed) passed++;

            results.add(new IntentCaseResult(
                    c.input(),
                    casePassed,
                    String.join("|", c.expectedPlanTypes()),
                    actualType,
                    c.expectedAgentIds(),
                    actualAgents,
                    casePassed ? "OK" : describeIntentFailure(typeOk, agentOk)
            ));
        }

        return new IntentSuiteResult(
                cases.size(),
                passed,
                cases.size() - passed,
                System.currentTimeMillis() - start,
                results
        );
    }

    public Optional<PipelineTaskStatusDto> getPipelineTaskStatus(String taskId) {
        return pipelineTaskService.findByTaskId(taskId).map(this::toTaskStatus);
    }

    public Optional<PipelineTaskStatusDto> getLatestPipelineTask(Long userId) {
        return pipelineTaskService.findLatestByUserId(userId).map(this::toTaskStatus);
    }

    // ==================== internal ====================

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
                task.getErrorMessage()
        );
    }

    private static String describeIntentFailure(boolean typeOk, boolean agentOk) {
        if (!typeOk && !agentOk) return "planType 与 agentId 均不符合";
        if (!typeOk) return "planType 不符合";
        return "缺少期望 agentId";
    }

    private record IntentCaseDefinition(
            String input,
            List<String> expectedPlanTypes,
            List<String> expectedAgentIds
    ) {}

    private static List<IntentCaseDefinition> buildIntentCases() {
        return List.of(
                new IntentCaseDefinition("你好", List.of("SIMPLE"), List.of()),
                new IntentCaseDefinition("你能做什么", List.of("SIMPLE"), List.of()),
                new IntentCaseDefinition("什么是 Python 装饰器", List.of("SIMPLE", "PLAN", "PIPELINE"), List.of("qa_agent")),
                new IntentCaseDefinition("帮我学 Python", List.of("PLAN", "PIPELINE", "CLARIFY"), List.of("learning_path")),
                new IntentCaseDefinition("出 5 道闭包练习题", List.of("PLAN", "PIPELINE"), List.of("quiz_agent")),
                new IntentCaseDefinition("用视频解释什么是 Agent", List.of("PLAN", "PIPELINE"), List.of("media_gen")),
                new IntentCaseDefinition("画一张装饰器示意图", List.of("PLAN", "PIPELINE"), List.of("media_gen")),
                new IntentCaseDefinition("生成 Python 学习路线图", List.of("PLAN", "PIPELINE"), List.of("learning_path"))
        );
    }
}
