package com.lqragent.backend.orchestrator.planning;

import java.util.List;

import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineStep;

/**
 * 规划结果
 * - 简单请求（问候/帮助/QA）→ type=SIMPLE，intent 标识
 * - 复杂请求 → type=PIPELINE，携带 PipelineConfig
 * - 需求确认 → type=CLARIFY，携带需要询问的问题
 */
public record PlanResult(
        PlanType type,
        PlanIntent intent,
        PipelineConfig pipelineConfig,
        List<PipelineStep> steps,
        List<String> clarifyQuestions,
        String clarifyContext
) {
    public enum PlanType { SIMPLE, PIPELINE, CLARIFY }

    public boolean isSimple() {
        return type == PlanType.SIMPLE;
    }

    public boolean isPipeline() {
        return type == PlanType.PIPELINE;
    }

    public boolean isClarify() {
        return type == PlanType.CLARIFY;
    }

    public static PlanResult simple(PlanIntent intent) {
        return new PlanResult(PlanType.SIMPLE, intent, null, null, null, null);
    }

    public static PlanResult pipeline(PipelineConfig config, List<PipelineStep> steps) {
        return new PlanResult(PlanType.PIPELINE, null, config, steps, null, null);
    }

    public static PlanResult clarify(List<String> questions, String context) {
        return new PlanResult(PlanType.CLARIFY, null, null, null, questions, context);
    }
}
