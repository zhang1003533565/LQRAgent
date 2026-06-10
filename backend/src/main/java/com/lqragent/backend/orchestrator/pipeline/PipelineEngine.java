package com.lqragent.backend.orchestrator.pipeline;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.lqragent.backend.agents.base.AgentInterface;
import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.context.TraceSpan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline 流水线引擎
 * 根据配置的步骤依赖关系，编排和执行 Agent 任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineEngine {

    private final AgentRegistry agentRegistry;
    
    /** 内置模板注册表 */
    private final Map<String, PipelineConfig> templateRegistry = new ConcurrentHashMap<>();

    /** 步骤完成回调（用于异步模式下向前端推送进度） */
    @FunctionalInterface
    public interface StepCallback {
        void onStepComplete(String stepId, String agentId, boolean success, Map<String, Object> data);
    }

    /**
     * 注册模板
     */
    public void registerTemplate(PipelineConfig config) {
        templateRegistry.put(config.getPipelineId(), config);
        log.info("[Pipeline] registered template: {}", config.getPipelineId());
    }

    /**
     * 根据ID获取模板
     */
    public PipelineConfig getTemplate(String pipelineId) {
        return templateRegistry.get(pipelineId);
    }

    /**
     * 执行 Pipeline
     */
    public PipelineResult execute(PipelineConfig config, TaskContext context) {
        long startTime = System.currentTimeMillis();
        String traceId = context.getTaskId();
        
        log.info("[Pipeline] start: {}, steps={}, parallel={}", 
                config.getName(), config.getSteps().size(), config.isParallel());

        try {
            // 构建执行图
            ExecutionGraph graph = buildGraph(config);
            List<StepResult> allResults = new CopyOnWriteArrayList<>();

            // 按拓扑顺序执行
            while (graph.hasReadySteps()) {
                List<PipelineStep> readySteps = graph.getReadySteps();

                if (config.isParallel() && readySteps.size() > 1) {
                    // 并行执行
                    executeParallel(readySteps, context, allResults, graph);
                } else {
                    // 串行执行
                    for (PipelineStep step : readySteps) {
                        StepResult result = executeStep(step, context, traceId);
                        allResults.add(result);
                        graph.markCompleted(step.getStepId(), result.isSuccess());
                    }
                }
            }

            // 检查是否所有步骤都成功
            boolean allSuccess = allResults.stream().allMatch(StepResult::isSuccess);
            long totalDuration = System.currentTimeMillis() - startTime;

            if (allSuccess) {
                // 获取最后一个步骤的结果作为最终输出
                Map<String, Object> output = allResults.isEmpty() ? 
                        Map.of() : allResults.get(allResults.size() - 1).getData();
                return PipelineResult.success(allResults, output, totalDuration);
            } else {
                String errorMsg = allResults.stream()
                        .filter(r -> !r.isSuccess())
                        .map(StepResult::getErrorMessage)
                        .findFirst()
                        .orElse("Unknown error");
                return PipelineResult.failure(errorMsg, totalDuration);
            }

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            log.error("[Pipeline] execution failed: {}", e.getMessage());
            return PipelineResult.failure(e.getMessage(), totalDuration);
        }
    }

    /**
     * 执行 Pipeline（带步骤回调，用于异步模式）
     */
    public PipelineResult execute(PipelineConfig config, TaskContext context, StepCallback callback) {
        long startTime = System.currentTimeMillis();
        String traceId = context.getTaskId();
        
        log.info("[Pipeline] start(async): {}, steps={}", 
                config.getName(), config.getSteps().size());

        try {
            ExecutionGraph graph = buildGraph(config);
            List<StepResult> allResults = new CopyOnWriteArrayList<>();

            while (graph.hasReadySteps()) {
                List<PipelineStep> readySteps = graph.getReadySteps();

                for (PipelineStep step : readySteps) {
                    StepResult result = executeStep(step, context, traceId);
                    allResults.add(result);
                    graph.markCompleted(step.getStepId(), result.isSuccess());
                    
                    // 步骤完成回调
                    if (callback != null) {
                        try {
                            callback.onStepComplete(
                                    result.getStepId(), result.getAgentId(),
                                    result.isSuccess(), result.getData());
                        } catch (Exception cbEx) {
                            log.warn("[Pipeline] step callback error: {}", cbEx.getMessage());
                        }
                    }
                }
            }

            boolean allSuccess = allResults.stream().allMatch(StepResult::isSuccess);
            long totalDuration = System.currentTimeMillis() - startTime;

            if (allSuccess) {
                Map<String, Object> output = allResults.isEmpty() ? 
                        Map.of() : allResults.get(allResults.size() - 1).getData();
                return PipelineResult.success(allResults, output, totalDuration);
            } else {
                String errorMsg = allResults.stream()
                        .filter(r -> !r.isSuccess())
                        .map(StepResult::getErrorMessage)
                        .findFirst()
                        .orElse("Unknown error");
                return PipelineResult.failure(errorMsg, totalDuration);
            }

        } catch (Exception e) {
            long totalDuration = System.currentTimeMillis() - startTime;
            log.error("[Pipeline] async execution failed: {}", e.getMessage());
            return PipelineResult.failure(e.getMessage(), totalDuration);
        }
    }

    /**
     * 执行单个步骤 — 通过 AgentRegistry 直接同步调用 Agent
     */
    private StepResult executeStep(PipelineStep step, TaskContext context, String traceId) {
        String spanId = "span-" + step.getStepId() + "-" + System.nanoTime();
        TraceSpan span = new TraceSpan(traceId, step.getStepId(), step.getAgentId());
        context.addTraceSpan(span);

        log.info("[Pipeline] executing step: {} -> {}", step.getStepId(), step.getAgentId());

        long stepStart = System.currentTimeMillis();
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= step.getMaxRetries()) {
            try {
                // 通过 AgentRegistry 获取 Agent
                AgentInterface agent = agentRegistry.getAgent(step.getAgentId())
                        .orElseThrow(() -> new RuntimeException(
                                "Agent not found in registry: " + step.getAgentId()));

                // 构建请求参数
                Map<String, Object> payload = buildPayload(step, context);

                // 构建 AgentRequest 并同步调用
                BaseAgent.AgentRequest request = new BaseAgent.AgentRequest(
                        step.getAction(),
                        context.getGoal(),
                        payload
                );

                BaseAgent.AgentResponse response = agent.process(request, context);

                // 将 AgentResponse 转为 Map，存入上下文供后续步骤使用
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", response.success());
                result.put("content", response.content());
                if (response.executions() != null && !response.executions().isEmpty()) {
                    result.put("toolCalls", response.executions().size());
                }
                if (response.error() != null) {
                    result.put("error", response.error());
                }

                // 存储步骤结果到上下文（供下游步骤的 resultMapping 使用）
                context.setResult(step.getStepId(), result);

                long duration = System.currentTimeMillis() - stepStart;
                span.complete(truncate(
                        response.success() ? response.content() : response.error(), 200));

                if (response.success()) {
                    StepResult stepResult = StepResult.success(
                            step.getStepId(), step.getAgentId(), result, duration);
                    stepResult.setRetryCount(retryCount);
                    graph_markCompleted(context, step.getStepId(), true);
                    return stepResult;
                } else {
                    // Agent 返回了失败结果，作为异常处理以触发重试
                    throw new RuntimeException("Agent returned failure: " + response.error());
                }

            } catch (Exception e) {
                retryCount++;
                lastException = e;
                log.warn("[Pipeline] step {} failed: {}, retry {}/{}",
                        step.getStepId(), e.getMessage(), retryCount, step.getMaxRetries());
            }
        }

        // 重试耗尽
        long duration = System.currentTimeMillis() - stepStart;
        span.fail(lastException != null ? lastException.getMessage() : "Max retries exceeded");

        if (step.isOptional()) {
            log.warn("[Pipeline] optional step {} failed, continuing...", step.getStepId());
            return StepResult.skipped(step.getStepId(), step.getAgentId(), lastException);
        }

        return StepResult.failure(step.getStepId(), step.getAgentId(),
                lastException != null ? lastException.getMessage() : "Max retries exceeded",
                duration);
    }

    /**
     * 并行执行多个步骤
     */
    private void executeParallel(List<PipelineStep> steps, TaskContext context,
                                  List<StepResult> allResults, ExecutionGraph graph) {
        ExecutorService executor = Executors.newFixedThreadPool(steps.size());
        List<Future<StepResult>> futures = new ArrayList<>();

        for (PipelineStep step : steps) {
            futures.add(executor.submit(() -> 
                    executeStep(step, context, context.getTaskId())));
        }

        for (int i = 0; i < futures.size(); i++) {
            try {
                StepResult result = futures.get(i).get(
                        steps.get(i).getTimeoutMs() + 1000, TimeUnit.MILLISECONDS);
                allResults.add(result);
                graph.markCompleted(steps.get(i).getStepId(), result.isSuccess());
            } catch (Exception e) {
                StepResult failResult = StepResult.failure(
                        steps.get(i).getStepId(), steps.get(i).getAgentId(),
                        e.getMessage(), 0);
                allResults.add(failResult);
                graph.markCompleted(steps.get(i).getStepId(), false);
            }
        }

        executor.shutdown();
    }

    /**
     * 构建执行参数
     */
    private Map<String, Object> buildPayload(PipelineStep step, TaskContext context) {
        Map<String, Object> payload = new HashMap<>();
        
        // 添加全局参数
        if (step.getParams() != null) {
            payload.putAll(step.getParams());
        }

        // 从依赖步骤提取结果
        if (step.getResultMapping() != null && !step.getResultMapping().isEmpty()) {
            for (Map.Entry<String, String> mapping : step.getResultMapping().entrySet()) {
                String sourceStep = mapping.getKey();
                String targetKey = mapping.getValue();
                Map<String, Object> sourceResult = context.getResult(sourceStep);
                if (sourceResult != null) {
                    payload.put(targetKey, sourceResult);
                }
            }
        }

        // 添加上下文信息
        payload.put("userId", context.getUserId());
        payload.put("sessionId", context.getSessionId());
        payload.put("goal", context.getGoal());

        return payload;
    }

    // ===== waitForResult removed — Pipeline now calls Agents synchronously via AgentRegistry =====

    /**
     * 构建执行图（拓扑排序）
     */
    private ExecutionGraph buildGraph(PipelineConfig config) {
        return new ExecutionGraph(config.getSteps());
    }

    private void graph_markCompleted(TaskContext context, String stepId, boolean success) {
        // 可选：记录到上下文
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    /**
     * 执行图（管理依赖关系）
     */
    static class ExecutionGraph {
        private final Map<String, PipelineStep> steps;
        private final Set<String> completed = new HashSet<>();
        private final Set<String> failed = new HashSet<>();

        ExecutionGraph(List<PipelineStep> steps) {
            this.steps = steps.stream()
                    .collect(Collectors.toMap(PipelineStep::getStepId, s -> s));
        }

        boolean hasReadySteps() {
            return steps.keySet().stream()
                    .filter(id -> !completed.contains(id) && !failed.contains(id))
                    .anyMatch(this::areDependenciesMet);
        }

        List<PipelineStep> getReadySteps() {
            return steps.keySet().stream()
                    .filter(id -> !completed.contains(id) && !failed.contains(id))
                    .filter(this::areDependenciesMet)
                    .map(steps::get)
                    .collect(Collectors.toList());
        }

        void markCompleted(String stepId, boolean success) {
            if (success) {
                completed.add(stepId);
            } else {
                failed.add(stepId);
            }
        }

        private boolean areDependenciesMet(String stepId) {
            PipelineStep step = steps.get(stepId);
            if (step.getDependsOn() == null || step.getDependsOn().isEmpty()) {
                return true;
            }
            if (step.getConditionType() == PipelineStep.ConditionType.ANY_COMPLETED) {
                return step.getDependsOn().stream().anyMatch(completed::contains);
            }
            return step.getDependsOn().stream().allMatch(id -> 
                    completed.contains(id) || failed.contains(id));
        }
    }
}
