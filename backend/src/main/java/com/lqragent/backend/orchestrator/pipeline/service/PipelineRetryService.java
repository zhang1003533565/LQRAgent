package com.lqragent.backend.orchestrator.pipeline.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
import com.lqragent.backend.orchestrator.pipeline.dto.PipelineTaskDto;
import com.lqragent.backend.orchestrator.pipeline.entity.PipelineTask;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.planning.PlanningAgent;
import com.lqragent.backend.orchestrator.planning.TaskPlan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline 断点重试服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineRetryService {

    private final PipelineTaskService pipelineTaskService;
    private final PipelineEngine pipelineEngine;
    private final PlanningAgent planningAgent;
    private final OrchestratorCore orchestratorCore;

    /**
     * 异步重试失败任务，立即返回 RUNNING 状态
     */
    public Optional<PipelineTaskDto> retryAsync(String taskId, Long userId) {
        Optional<PipelineTask> taskOpt = validateRetry(taskId, userId);
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        PipelineTask task = taskOpt.get();
        RetryContext ctx = buildRetryContext(task);
        if (ctx == null) {
            return Optional.empty();
        }

        pipelineTaskService.prepareForRetry(taskId);
        runRetry(task, ctx, null);
        return pipelineTaskService.findByTaskId(taskId).map(pipelineTaskService::toDto);
    }

    /**
     * 同步重试（控制台用），等待执行完成
     */
    public Optional<PipelineTaskDto> retrySync(String taskId, Long userId, long timeoutMs) {
        Optional<PipelineTask> taskOpt = validateRetry(taskId, userId);
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        PipelineTask task = taskOpt.get();
        RetryContext ctx = buildRetryContext(task);
        if (ctx == null) {
            return Optional.empty();
        }

        pipelineTaskService.prepareForRetry(taskId);
        CompletableFuture<Void> done = new CompletableFuture<>();
        runRetry(task, ctx, done);
        try {
            done.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("[PipelineRetry] sync wait interrupted: {}", e.getMessage());
        }
        return pipelineTaskService.findByTaskId(taskId).map(pipelineTaskService::toDto);
    }

    private Optional<PipelineTask> validateRetry(String taskId, Long userId) {
        Optional<PipelineTask> taskOpt = pipelineTaskService.findByTaskId(taskId);
        if (taskOpt.isEmpty()) {
            return Optional.empty();
        }
        PipelineTask task = taskOpt.get();
        if (userId != null && !userId.equals(task.getUserId())) {
            log.warn("[PipelineRetry] user {} cannot retry task owned by {}", userId, task.getUserId());
            return Optional.empty();
        }
        if (!"FAILED".equals(task.getStatus())) {
            log.warn("[PipelineRetry] task {} status is {}, not FAILED", taskId, task.getStatus());
            return Optional.empty();
        }
        if (task.getFailedStep() == null || task.getFailedStep().isBlank()) {
            log.warn("[PipelineRetry] task {} has no failed_step", taskId);
            return Optional.empty();
        }
        return taskOpt;
    }

    private RetryContext buildRetryContext(PipelineTask task) {
        PipelineConfig config = pipelineTaskService.loadPipelineConfig(task)
                .orElseGet(() -> replanConfig(task));
        if (config == null || config.getSteps() == null || config.getSteps().isEmpty()) {
            log.error("[PipelineRetry] no pipeline config for task {}", task.getTaskId());
            return null;
        }
        List<StepResult> completed = pipelineTaskService.loadCompletedStepResults(task);
        return new RetryContext(config, task.getFailedStep(), completed);
    }

    private PipelineConfig replanConfig(PipelineTask task) {
        try {
            PlanResult plan = planningAgent.decompose(task.getGoal(), String.valueOf(task.getUserId()));
            if (plan.isPlan() && plan.taskPlan() != null) {
                TaskPlan taskPlan = plan.taskPlan();
                return orchestratorCore.buildPipelineFromPlan(taskPlan);
            }
            if (plan.isPipeline() && plan.pipelineConfig() != null) {
                return plan.pipelineConfig();
            }
        } catch (Exception e) {
            log.warn("[PipelineRetry] replan failed for {}: {}", task.getTaskId(), e.getMessage());
        }
        return null;
    }

    private void runRetry(PipelineTask task, RetryContext ctx, CompletableFuture<Void> done) {
        String taskId = task.getTaskId();
        Long uid = task.getUserId();
        TaskContext context = new TaskContext(taskId, String.valueOf(uid), null, task.getGoal());

        Thread worker = new Thread(() -> {
            try {
                PipelineResult result = pipelineEngine.executeFromStep(
                        ctx.config(),
                        context,
                        ctx.fromStepId(),
                        ctx.completedResults(),
                        (stepId, agentId, success, data) -> {
                            pipelineTaskService.updateCurrentStep(taskId, stepId, stepId);
                            pipelineTaskService.recordStepResult(taskId, stepId, agentId, success, data, 0);
                        });

                if (result.isSuccess()) {
                    pipelineTaskService.markCompleted(taskId);
                } else {
                    String failedStep = result.getStepResults().stream()
                            .filter(r -> !r.isSuccess())
                            .map(StepResult::getStepId)
                            .findFirst()
                            .orElse(ctx.fromStepId());
                    pipelineTaskService.markFailed(taskId, result.getErrorMessage(), failedStep);
                }
            } catch (Throwable e) {
                log.error("[PipelineRetry] task {} failed: {}", taskId, e.getMessage(), e);
                pipelineTaskService.markFailed(taskId, e.getMessage(), ctx.fromStepId());
            } finally {
                if (done != null) {
                    done.complete(null);
                }
            }
        }, "pipeline-retry-" + taskId);
        worker.setDaemon(true);
        worker.start();
    }

    private record RetryContext(
            PipelineConfig config,
            String fromStepId,
            List<StepResult> completedResults
    ) {}
}
