package com.lqragent.backend.orchestrator.pipeline.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.pipeline.dto.PipelineTaskDto;
import com.lqragent.backend.orchestrator.pipeline.entity.PipelineTask;
import com.lqragent.backend.orchestrator.pipeline.repository.PipelineTaskRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline 任务持久化服务
 * 负责 Pipeline 执行过程中的任务创建、状态更新、查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineTaskService {

    private final PipelineTaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建一条新的 Pipeline 任务记录
     */
    @Transactional
    public PipelineTask createTask(String taskId, Long userId, String pipelineId,
                                    String pipelineName, String goal, int stepCount) {
        PipelineTask task = PipelineTask.builder()
                .taskId(taskId)
                .userId(userId)
                .pipelineId(pipelineId)
                .pipelineName(pipelineName)
                .goal(goal)
                .status("RUNNING")
                .stepCount(stepCount)
                .completedSteps(0)
                .currentStep("")
                .stepResultsJson("[]")
                .errorMessage("")
                .build();
        return taskRepository.save(task);
    }

    /**
     * 更新当前执行的步骤
     */
    @Transactional
    public void updateCurrentStep(String taskId, String stepId, String stepName) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setCurrentStep(stepName != null ? stepName : stepId);
            taskRepository.save(task);
        });
    }

    /**
     * 记录步骤完成结果
     */
    @Transactional
    public void recordStepResult(String taskId, String stepId, String agentId,
                                  boolean success, Map<String, Object> data, long durationMs) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            try {
                // 读取已有步骤结果列表
                List<Map<String, Object>> results = readStepResults(task.getStepResultsJson());

                Map<String, Object> stepResult = Map.of(
                        "stepId", stepId,
                        "agentId", agentId,
                        "success", success,
                        "durationMs", durationMs,
                        "summary", data != null && data.containsKey("content")
                                ? truncate(String.valueOf(data.get("content")), 200)
                                : ""
                );
                results.add(stepResult);

                task.setStepResultsJson(objectMapper.writeValueAsString(results));
                task.setCompletedSteps(results.size());
                if (!success) {
                    task.setErrorMessage(data != null && data.containsKey("error")
                            ? String.valueOf(data.get("error"))
                            : "Step " + stepId + " failed");
                }
                taskRepository.save(task);
            } catch (JsonProcessingException e) {
                log.warn("[PipelineTask] failed to serialize step results: {}", e.getMessage());
            }
        });
    }

    /**
     * 标记任务完成
     */
    @Transactional
    public void markCompleted(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("COMPLETED");
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /**
     * 标记任务失败
     */
    @Transactional
    public void markFailed(String taskId, String errorMessage) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("FAILED");
            task.setCompletedAt(LocalDateTime.now());
            if (errorMessage != null && !errorMessage.isBlank()) {
                task.setErrorMessage(truncate(errorMessage, 500));
            }
            taskRepository.save(task);
        });
    }

    /**
     * 按 taskId 查询
     */
    @Transactional(readOnly = true)
    public Optional<PipelineTask> findByTaskId(String taskId) {
        return taskRepository.findByTaskId(taskId);
    }

    /**
     * 查询用户最近的任务
     */
    @Transactional(readOnly = true)
    public Optional<PipelineTask> findLatestByUserId(Long userId) {
        return taskRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 查询用户所有任务
     */
    @Transactional(readOnly = true)
    public List<PipelineTask> findByUserId(Long userId) {
        return taskRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * 转为 DTO（隐藏内部细节，只暴露前端需要的字段）
     */
    public PipelineTaskDto toDto(PipelineTask task) {
        List<Map<String, Object>> rawResults = readStepResults(task.getStepResultsJson());
        List<PipelineTaskDto.StepProgress> progressList = rawResults.stream().map(m ->
                PipelineTaskDto.StepProgress.builder()
                        .stepId((String) m.getOrDefault("stepId", ""))
                        .agentId((String) m.getOrDefault("agentId", ""))
                        .status(Boolean.TRUE.equals(m.get("success")) ? "done" : "failed")
                        .durationMs(m.containsKey("durationMs") ? ((Number) m.get("durationMs")).longValue() : 0L)
                        .build()
        ).toList();

        return PipelineTaskDto.builder()
                .taskId(task.getTaskId())
                .pipelineId(task.getPipelineId())
                .pipelineName(task.getPipelineName())
                .goal(task.getGoal())
                .status(task.getStatus())
                .stepCount(task.getStepCount())
                .completedSteps(task.getCompletedSteps())
                .currentStep(task.getCurrentStep())
                .stepProgressList(progressList)
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }

    // ===== 内部工具方法 =====

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readStepResults(String json) {
        if (json == null || json.isBlank()) {
            return new java.util.ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new java.util.ArrayList<>();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
