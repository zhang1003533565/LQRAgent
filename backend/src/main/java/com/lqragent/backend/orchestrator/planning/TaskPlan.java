package com.lqragent.backend.orchestrator.planning;

import java.util.List;

/**
 * 阶段二新增：结构化任务计划
 * <p>
 * LLM 基于 AgentCardRegistry 能力目录产出，由 OrchestratorCore 转为 PipelineConfig 执行
 */
public record TaskPlan(
        String planId,
        String userId,
        String goal,
        List<TaskStep> steps,
        List<String> expectedOutputs,
        String fallbackStrategy
) {
    public static TaskPlan of(String goal, String userId, List<TaskStep> steps) {
        return new TaskPlan(
                "plan-" + System.currentTimeMillis(),
                userId, goal, steps, List.of("text"), "qa"
        );
    }
}
