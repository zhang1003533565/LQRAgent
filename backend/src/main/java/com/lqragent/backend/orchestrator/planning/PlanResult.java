package com.lqragent.backend.orchestrator.planning;

import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineStep;

import java.util.List;

/**
 * 规划结果
 * - 简单请求（问候/帮助/QA）→ type=SIMPLE，intent 标识
 * - 复杂请求 → type=PIPELINE，携带 PipelineConfig
 */
public record PlanResult(
        PlanType type,
        PlanIntent intent,
        PipelineConfig pipelineConfig,
        List<PipelineStep> steps
) {
    public enum PlanType { SIMPLE, PIPELINE }

    public boolean isSimple() {
        return type == PlanType.SIMPLE;
    }

    public boolean isPipeline() {
        return type == PlanType.PIPELINE;
    }

    public static PlanResult simple(PlanIntent intent) {
        return new PlanResult(PlanType.SIMPLE, intent, null, null);
    }

    public static PlanResult pipeline(PipelineConfig config, List<PipelineStep> steps) {
        return new PlanResult(PlanType.PIPELINE, null, config, steps);
    }
}
