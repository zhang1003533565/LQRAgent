package com.lqragent.backend.admin.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateResponse;
import com.lqragent.backend.agents.effectassessment.service.EffectAssessmentService;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.agents.resourcegeneration.service.ResourceGenerationService;
import com.lqragent.backend.agents.qa.QaAgent;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.orchestrator.test.OrchestratorTestService;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AgentRunTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.CapabilityTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentSuiteResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.LearningLoopTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PipelineTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PlanTestResult;

import lombok.RequiredArgsConstructor;

/**
 * Agent / Orchestrator 测试 REST API — 免认证，供开发控制台与 Swagger 调试。
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class AgentTestController {

    private final OrchestratorTestService orchestratorTestService;
    private final LearningPathService learningPathService;
    private final ResourceGenerationService resourceGenerationService;
    private final EffectAssessmentService effectAssessmentService;
    private final QaAgent qaAgent;

    private static String userId(Map<String, String> body) {
        return body.getOrDefault("userId", "1");
    }

    // ===== Sprint 1：Orchestrator 测试 =====

    /** 仅意图规划，不执行 Pipeline */
    @PostMapping("/plan")
    public PlanTestResult testPlan(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        String chatHistory = body.getOrDefault("chatHistory", null);
        return orchestratorTestService.planOnly(userId(body), message, chatHistory);
    }

    /** 同步执行 plan + pipeline */
    @PostMapping("/pipeline")
    public PipelineTestResult testPipeline(@RequestBody Map<String, String> body) {
        return orchestratorTestService.runPipelineSync(
                userId(body), body.getOrDefault("message", ""));
    }

    /** AgentCard 能力目录 */
    @GetMapping("/agent-cards")
    public Map<String, Object> listAgentCards() {
        var cards = orchestratorTestService.listAgentCards();
        return Map.of(
                "success", true,
                "count", cards.size(),
                "cards", cards
        );
    }

    /** 已封装的 ai-server 工具列表 */
    @GetMapping("/aiserver-tools")
    public Map<String, Object> listAiServerTools() {
        var tools = orchestratorTestService.listAiServerTools();
        return Map.of("success", true, "tools", tools);
    }

    /** 测试 ai-server capability */
    @PostMapping("/capability/{name}")
    public CapabilityTestResult testCapability(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> body) {
        return orchestratorTestService.testCapability(name, body != null ? body : Map.of());
    }

    /** 同步执行 learning_loop Pipeline */
    @PostMapping("/learning-loop")
    public LearningLoopTestResult testLearningLoop(@RequestBody Map<String, Object> body) {
        Long userId = body.containsKey("userId")
                ? ((Number) body.get("userId")).longValue() : 1L;
        Long questionId = body.containsKey("questionId")
                ? ((Number) body.get("questionId")).longValue() : 1L;
        String answer = String.valueOf(body.getOrDefault("answer", ""));
        boolean correct = body.containsKey("correct") && Boolean.TRUE.equals(body.get("correct"));
        int score = body.containsKey("score") ? ((Number) body.get("score")).intValue() : (correct ? 100 : 0);
        return orchestratorTestService.runLearningLoop(userId, questionId, answer, correct, score);
    }

    /** 批量意图回归 */
    @PostMapping("/intent-suite")
    public IntentSuiteResult testIntentSuite(@RequestBody(required = false) Map<String, String> body) {
        String uid = body != null ? body.getOrDefault("userId", "1") : "1";
        return orchestratorTestService.runIntentSuite(uid);
    }

    /** 学习者上下文摘要（profile + mastery + memory） */
    @GetMapping("/learner-context")
    public Map<String, Object> getLearnerContext(@RequestParam(defaultValue = "1") Long userId) {
        var ctx = orchestratorTestService.getLearnerContext(userId);
        return Map.of("success", true, "context", ctx);
    }

    /** Pipeline 任务状态 */
    @GetMapping("/pipeline-task/{taskId}")
    public Map<String, Object> getPipelineTask(@PathVariable String taskId) {
        return orchestratorTestService.getPipelineTaskStatus(taskId)
                .map(t -> Map.<String, Object>of("success", true, "task", t))
                .orElse(Map.of("success", false, "error", "任务不存在: " + taskId));
    }

    @GetMapping("/pipeline-task/latest")
    public Map<String, Object> getLatestPipelineTask(@RequestParam(defaultValue = "1") Long userId) {
        return orchestratorTestService.getLatestPipelineTask(userId)
                .map(t -> Map.<String, Object>of("success", true, "task", t))
                .orElse(Map.of("success", false, "error", "无任务记录"));
    }

    /** 从 failed_step 断点重试（同步等待） */
    @PostMapping("/pipeline-task/{taskId}/retry")
    public Map<String, Object> retryPipelineTask(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "1") Long userId) {
        return orchestratorTestService.retryPipelineTask(taskId, userId, true)
                .map(t -> Map.<String, Object>of("success", true, "task", t))
                .orElse(Map.of("success", false, "error", "重试失败：任务不存在、非 FAILED 或无 failed_step"));
    }

    /** 构造 FAILED 任务（step1 成功 + step2 失败），供断点重试联调 */
    @PostMapping("/pipeline-task/seed-failed")
    public Map<String, Object> seedFailedPipelineTask(@RequestParam(defaultValue = "1") Long userId) {
        return orchestratorTestService.seedFailedPipelineTask(userId)
                .map(t -> Map.<String, Object>of("success", true, "task", t))
                .orElse(Map.of("success", false, "error", "构造失败任务失败"));
    }

    // ===== 通用 Agent 测试（增强版） =====

    /** 通过 Orchestrator 完整路由（含 plan + pipeline 详情） */
    @PostMapping("/agent")
    public Map<String, Object> testAgent(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");
        AgentRunTestResult result = orchestratorTestService.runAgentRoute(userId(body), message);
        return toAgentResponseMap(result);
    }

    /** 直接测试 QA Agent（ReAct 模式） */
    @PostMapping("/qa-agent")
    public Map<String, Object> testQaAgent(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");

        long start = System.currentTimeMillis();
        try {
            AgentMessage request = AgentMessage.request("test", "admin", "qa_agent",
                    Map.of("goal", message));
            AgentMessage response = qaAgent.process(request);
            long duration = System.currentTimeMillis() - start;

            boolean success = response.getPerformative() == Performative.INFORM;
            Map<String, Object> data = new HashMap<>();
            data.put("success", success);
            data.put("content", success ? response.getContent().get("content") : null);
            data.put("error", success ? null : response.getContent().get("error"));
            data.put("durationMs", duration);
            return data;
        } catch (Exception e) {
            return Map.of(
                    "success", false,
                    "error", e.getMessage(),
                    "durationMs", System.currentTimeMillis() - start
            );
        }
    }

    // ===== 快捷测试端点（保留） =====

    @PostMapping("/path")
    public Map<String, Object> testPath(@RequestBody Map<String, String> body) {
        String goal = body.getOrDefault("goal", "Python装饰器");
        String currentKpId = body.getOrDefault("currentKpId", "");

        RequestContext.init(1L);
        long start = System.currentTimeMillis();

        try {
            LearningPathDto path = learningPathService.generatePath(1L, goal,
                    currentKpId.isBlank() ? null : currentKpId);
            long duration = System.currentTimeMillis() - start;

            return Map.of(
                    "success", true,
                    "pathId", path.getPathId(),
                    "goal", path.getGoal(),
                    "nodes", path.getNodes(),
                    "planDescription", path.getPlanDescription(),
                    "durationMs", duration
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    @PostMapping("/resource")
    public Map<String, Object> testResource(@RequestBody Map<String, String> body) {
        String kpId = body.getOrDefault("kpId", "kp_intro");
        String resourceType = body.getOrDefault("resourceType", "LESSON");

        RequestContext.init(1L);
        long start = System.currentTimeMillis();

        try {
            ResourceGenerateRequest req = ResourceGenerateRequest.builder()
                    .kpId(kpId)
                    .resourceType(resourceType)
                    .build();
            ResourceGenerateResponse resp = resourceGenerationService.generate(req);
            long duration = System.currentTimeMillis() - start;

            return Map.of(
                    "success", true,
                    "resourceId", resp.getResourceId(),
                    "kpId", resp.getKpId(),
                    "resourceType", resp.getResourceType(),
                    "title", resp.getTitle(),
                    "content", resp.getContent(),
                    "existingCount", resp.getExistingCount(),
                    "durationMs", duration
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /**
     * 模拟答题提交
     * mode=loop（默认）→ learning_loop Pipeline（与线上一致）
     * mode=legacy → EffectAssessmentService
     */
    @PostMapping("/quiz-submit")
    public Map<String, Object> testQuizSubmit(@RequestBody Map<String, Object> body) {
        String mode = String.valueOf(body.getOrDefault("mode", "loop"));

        if ("loop".equalsIgnoreCase(mode)) {
            Long userId = body.containsKey("userId")
                    ? ((Number) body.get("userId")).longValue() : 1L;
            Long questionId = body.containsKey("questionId")
                    ? ((Number) body.get("questionId")).longValue() : 1L;
            String answer = String.valueOf(body.getOrDefault("answer", ""));
            boolean correct = body.containsKey("correct") && Boolean.TRUE.equals(body.get("correct"));
            int score = body.containsKey("score") ? ((Number) body.get("score")).intValue() : 30;

            LearningLoopTestResult loop = orchestratorTestService.runLearningLoop(
                    userId, questionId, answer, correct, score);
            Map<String, Object> data = new HashMap<>();
            data.put("success", loop.success());
            data.put("mode", "loop");
            data.put("expectedStepIds", loop.expectedStepIds());
            data.put("stepsAligned", loop.stepsAligned());
            data.put("stepResults", loop.stepResults());
            data.put("durationMs", loop.durationMs());
            data.put("error", loop.error());
            return data;
        }

        String kpId = (String) body.getOrDefault("kpId", "kp_intro");
        int score = body.containsKey("score") ? ((Number) body.get("score")).intValue() : 30;
        boolean correct = body.containsKey("correct") && Boolean.TRUE.equals(body.get("correct"));

        RequestContext.init(1L);

        try {
            effectAssessmentService.evaluateQuizResult(1L, kpId, score, correct);
            return Map.of(
                    "success", true,
                    "mode", "legacy",
                    "message", "答题评估完成（EffectAssessmentService）",
                    "kpId", kpId,
                    "score", score,
                    "correct", correct
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    private static Map<String, Object> toAgentResponseMap(AgentRunTestResult result) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", result.success());
        data.put("route", result.route());
        data.put("response", result.response());
        data.put("agent", result.agent());
        data.put("durationMs", result.durationMs());
        data.put("error", result.error());
        if (result.plan() != null) {
            data.put("planType", result.plan().planType());
            data.put("intent", result.plan().intent());
            data.put("steps", result.plan().steps());
            data.put("pipelineId", result.plan().pipelineId());
            data.put("pipelineName", result.plan().pipelineName());
        }
        if (result.pipeline() != null) {
            data.put("stepResults", result.pipeline().stepResults());
            data.put("artifacts", result.pipeline().artifacts());
            data.put("pipelineSuccess", result.pipeline().success());
            data.put("pipelineError", result.pipeline().error());
        }
        return data;
    }
}
