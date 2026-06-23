package com.lqragent.backend.orchestrator.test.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lqragent.backend.orchestrator.artifact.Artifact;

/**
 * Orchestrator 测试 API / 控制台命令 统一返回结构
 */
public final class OrchestratorTestDtos {

    private OrchestratorTestDtos() {}

    public record PlanStepDto(
            String stepId,
            String agentId,
            String action,
            List<String> dependsOn,
            String outputKind
    ) {}

    public record StepResultDto(
            String stepId,
            String agentId,
            boolean success,
            long durationMs,
            int retryCount,
            String summary,
            String error,
            List<Artifact> artifacts
    ) {}

    public record PlanTestResult(
            boolean success,
            String planType,
            String intent,
            String pipelineId,
            String pipelineName,
            String goal,
            List<PlanStepDto> steps,
            List<String> clarifyQuestions,
            String clarifyContext,
            String route,
            String response,
            long durationMs,
            String error
    ) {}

    public record PipelineTestResult(
            boolean success,
            PlanTestResult plan,
            List<StepResultDto> stepResults,
            List<Artifact> artifacts,
            long durationMs,
            String error
    ) {}

    public record AgentCardDto(
            String agentId,
            String displayName,
            String description,
            List<String> capabilities,
            List<String> outputArtifactKinds,
            int toolCount
    ) {}

    public record CapabilityTestResult(
            boolean success,
            String capability,
            String result,
            long durationMs,
            String error
    ) {}

    public record LearningLoopTestResult(
            boolean success,
            List<StepResultDto> stepResults,
            List<String> expectedStepIds,
            boolean stepsAligned,
            long durationMs,
            String error
    ) {}

    public record IntentCaseResult(
            String input,
            boolean passed,
            String expectedPlanType,
            String actualPlanType,
            List<String> expectedAgentIds,
            List<String> actualAgentIds,
            String note
    ) {}

    public record IntentSuiteResult(
            int total,
            int passed,
            int failed,
            long durationMs,
            List<IntentCaseResult> cases
    ) {}

    public record AgentRunTestResult(
            boolean success,
            String route,
            String agent,
            String response,
            PlanTestResult plan,
            PipelineTestResult pipeline,
            long durationMs,
            String error
    ) {}

    public record PipelineTaskStatusDto(
            String taskId,
            String status,
            String pipelineName,
            String goal,
            int stepCount,
            int completedSteps,
            String currentStep,
            String failedStep,
            String errorMessage
    ) {}

    public record AiServerToolDto(
            String name,
            String description
    ) {}

    /** 将 PlanTestResult 转为 Map（供 JSON 嵌套） */
    public static Map<String, Object> toMap(PlanTestResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", r.success());
        m.put("planType", r.planType() != null ? r.planType() : "");
        m.put("intent", r.intent() != null ? r.intent() : "");
        m.put("pipelineId", r.pipelineId() != null ? r.pipelineId() : "");
        m.put("pipelineName", r.pipelineName() != null ? r.pipelineName() : "");
        m.put("goal", r.goal() != null ? r.goal() : "");
        m.put("steps", r.steps() != null ? r.steps() : List.of());
        m.put("clarifyQuestions", r.clarifyQuestions() != null ? r.clarifyQuestions() : List.of());
        m.put("route", r.route() != null ? r.route() : "");
        m.put("response", r.response() != null ? truncate(r.response(), 500) : "");
        m.put("durationMs", r.durationMs());
        m.put("error", r.error() != null ? r.error() : "");
        return m;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
