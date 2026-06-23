package com.lqragent.backend.orchestrator.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.artifact.Artifact;
import com.lqragent.backend.orchestrator.artifact.ArtifactExtractor;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineStep;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.planning.TaskPlan;
import com.lqragent.backend.orchestrator.planning.TaskStep;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.AgentCardDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PlanStepDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PlanTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.StepResultDto;

/**
 * 测试 DTO 转换与 Artifact 提取工具
 */
public final class OrchestratorTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OrchestratorTestSupport() {}

    public static PlanTestResult fromPlanResult(PlanResult plan, String goal, long durationMs) {
        if (plan == null) {
            return new PlanTestResult(false, null, null, null, null, goal,
                    List.of(), List.of(), null, null, null, durationMs, "plan 为 null");
        }

        List<PlanStepDto> steps = extractSteps(plan);
        String pipelineId = plan.pipelineConfig() != null ? plan.pipelineConfig().getPipelineId() : null;
        String pipelineName = plan.pipelineConfig() != null ? plan.pipelineConfig().getName() : null;

        if (plan.taskPlan() != null && pipelineId == null) {
            pipelineId = plan.taskPlan().planId();
            pipelineName = plan.taskPlan().goal();
        }

        String intent = plan.intent() != null ? plan.intent().name() : null;

        return new PlanTestResult(
                true,
                plan.type().name(),
                intent,
                pipelineId,
                pipelineName,
                goal,
                steps,
                plan.clarifyQuestions() != null ? plan.clarifyQuestions() : List.of(),
                plan.clarifyContext(),
                null,
                null,
                durationMs,
                null
        );
    }

    public static List<PlanStepDto> extractSteps(PlanResult plan) {
        List<PlanStepDto> steps = new ArrayList<>();
        if (plan.pipelineConfig() != null && plan.pipelineConfig().getSteps() != null) {
            for (PipelineStep s : plan.pipelineConfig().getSteps()) {
                steps.add(new PlanStepDto(
                        s.getStepId(),
                        s.getAgentId(),
                        s.getAction(),
                        s.getDependsOn() != null ? s.getDependsOn() : List.of(),
                        null
                ));
            }
        } else if (plan.taskPlan() != null && plan.taskPlan().steps() != null) {
            for (TaskStep s : plan.taskPlan().steps()) {
                steps.add(new PlanStepDto(
                        s.getStepId(),
                        s.getAgentId(),
                        s.getAction(),
                        s.getDependsOn() != null ? s.getDependsOn() : List.of(),
                        s.getOutputKind()
                ));
            }
        } else if (plan.steps() != null) {
            for (PipelineStep s : plan.steps()) {
                steps.add(new PlanStepDto(
                        s.getStepId(),
                        s.getAgentId(),
                        s.getAction(),
                        s.getDependsOn() != null ? s.getDependsOn() : List.of(),
                        null
                ));
            }
        }
        return steps;
    }

    public static StepResultDto toStepResultDto(StepResult sr) {
        Map<String, Object> data = sr.getData() != null ? sr.getData() : Map.of();
        String summary = summarizeStepData(data);
        return new StepResultDto(
                sr.getStepId(),
                sr.getAgentId(),
                sr.isSuccess(),
                sr.getDurationMs(),
                sr.getRetryCount(),
                summary,
                sr.getErrorMessage(),
                extractArtifactsFromData(sr.getAgentId(), data)
        );
    }

    public static List<Artifact> extractArtifactsFromData(String agentId, Map<String, Object> data) {
        return ArtifactExtractor.fromStepData(agentId, data);
    }

    public static List<Artifact> collectAllArtifacts(List<StepResultDto> stepResults) {
        List<Artifact> all = new ArrayList<>();
        if (stepResults == null) return all;
        for (StepResultDto sr : stepResults) {
            if (sr.artifacts() != null) {
                all.addAll(sr.artifacts());
            }
        }
        return all;
    }

    public static AgentCardDto toAgentCardDto(AgentCard card) {
        List<ToolSpec> tools = card.tools() != null ? card.tools() : List.of();
        return new AgentCardDto(
                card.agentId(),
                card.displayName(),
                card.description(),
                card.capabilities() != null ? card.capabilities() : List.of(),
                card.outputArtifactKinds() != null ? card.outputArtifactKinds() : List.of(),
                tools.size()
        );
    }

    public static String summarizeStepData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return "";
        Object content = data.get("content");
        if (content != null && !String.valueOf(content).isBlank()) {
            return truncate(String.valueOf(content), 200);
        }
        Object kind = data.get("artifactKind");
        if (kind != null) {
            return "artifact:" + kind;
        }
        return truncate(data.toString(), 200);
    }

    public static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public static Map<String, Object> parseJsonArgs(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of("question", json, "topic", json, "data", json);
        }
    }

    public static PlanTestResult withRoute(PlanTestResult base, String route, String response) {
        return new PlanTestResult(
                base.success(), base.planType(), base.intent(), base.pipelineId(), base.pipelineName(),
                base.goal(), base.steps(), base.clarifyQuestions(), base.clarifyContext(),
                route, response, base.durationMs(), base.error()
        );
    }

    public static boolean isPipelinePlan(PlanResult plan) {
        return plan != null && plan.isPipeline() && plan.pipelineConfig() != null
                && plan.pipelineConfig().getSteps() != null
                && !plan.pipelineConfig().getSteps().isEmpty();
    }

    public static List<String> agentIdsFromPlan(PlanResult plan) {
        return extractSteps(plan).stream()
                .map(PlanStepDto::agentId)
                .distinct()
                .toList();
    }
}
