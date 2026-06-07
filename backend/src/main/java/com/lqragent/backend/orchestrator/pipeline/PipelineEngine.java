package com.lqragent.backend.orchestrator.pipeline;

import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.context.TraceSpan;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Pipeline 流水线引擎
 * 根据配置的步骤依赖关系，编排和执行 Agent 任务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineEngine {

    private final RedisStreamsService streams;
    
    /** 内置模板注册表 */
    private final Map<String, PipelineConfig> templateRegistry = new ConcurrentHashMap<>();

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
     * 执行单个步骤
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
                // 构建参数
                Map<String, Object> payload = buildPayload(step, context);
                payload.put("action", step.getAction());
                payload.put("traceId", traceId);
                payload.put("spanId", spanId);

                // 发送任务到 Agent
                AgentMessage msg = AgentMessage.request(
                        context.getTaskId(),
                        "pipeline_engine",
                        step.getAgentId(),
                        payload
                );
                streams.send("stream:agent:" + step.getAgentId(), msg);

                // 等待结果（带超时）
                Map<String, Object> result = waitForResult(
                        context.getTaskId(), step.getAgentId(), step.getTimeoutMs());

                long duration = System.currentTimeMillis() - stepStart;
                span.complete(truncate(String.valueOf(result), 200));

                StepResult stepResult = StepResult.success(
                        step.getStepId(), step.getAgentId(), result, duration);
                stepResult.setRetryCount(retryCount);
                
                graph_markCompleted(context, step.getStepId(), true);
                return stepResult;

            } catch (TimeoutException e) {
                retryCount++;
                lastException = e;
                log.warn("[Pipeline] step {} timeout, retry {}/{}", 
                        step.getStepId(), retryCount, step.getMaxRetries());
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

    /**
     * 等待 Agent 结果
     */
    private Map<String, Object> waitForResult(String taskId, String agentId, long timeoutMs) 
            throws TimeoutException {
        long startTime = System.currentTimeMillis();
        String stream = "stream:agent:events";
        String group = "group:pipeline";
        streams.createGroup(stream, group);

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                var messages = streams.consumePending(stream, group, "pipeline:worker-1", 10);
                for (AgentMessage msg : messages) {
                    if (!taskId.equals(msg.getTaskId())) continue;
                    if (msg.getPerformative() == Performative.INFORM) {
                        return msg.getContent();
                    }
                    if (msg.getPerformative() == Performative.ERROR) {
                        throw new RuntimeException(
                                (String) msg.getContent().getOrDefault("error", "Unknown error"));
                    }
                }
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for result", e);
            }
        }

        throw new TimeoutException("Timeout waiting for agent: " + agentId);
    }

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
