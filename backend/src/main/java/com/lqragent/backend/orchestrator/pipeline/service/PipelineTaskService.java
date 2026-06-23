package com.lqragent.backend.orchestrator.pipeline.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.StepResult;
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
                                    String pipelineName, String goal, int stepCount,
                                    PipelineConfig config) {
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
                .pipelineConfigJson(serializeConfig(config))
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
     * 记录步骤完成结果（含完整 data，供断点重试恢复上下文）
     */
    @Transactional
    public void recordStepResult(String taskId, String stepId, String agentId,
                                  boolean success, Map<String, Object> data, long durationMs) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            try {
                List<Map<String, Object>> results = readStepResults(task.getStepResultsJson());

                Map<String, Object> stepResult = new LinkedHashMap<>();
                stepResult.put("stepId", stepId);
                stepResult.put("agentId", agentId);
                stepResult.put("success", success);
                stepResult.put("durationMs", durationMs);
                stepResult.put("summary", data != null && data.containsKey("content")
                        ? truncate(String.valueOf(data.get("content")), 200)
                        : "");
                if (data != null) {
                    stepResult.put("data", sanitizeForJson(data));
                    if (data.containsKey("error")) {
                        stepResult.put("error", String.valueOf(data.get("error")));
                    }
                }
                results.add(stepResult);

                task.setStepResultsJson(objectMapper.writeValueAsString(results));
                task.setCompletedSteps((int) results.stream()
                        .filter(m -> Boolean.TRUE.equals(m.get("success")))
                        .count());
                if (!success) {
                    task.setFailedStep(stepId);
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
            task.setFailedStep(null);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /**
     * 标记任务失败
     */
    @Transactional
    public void markFailed(String taskId, String errorMessage, String failedStep) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("FAILED");
            task.setCompletedAt(LocalDateTime.now());
            if (failedStep != null && !failedStep.isBlank()) {
                task.setFailedStep(failedStep);
            }
            if (errorMessage != null && !errorMessage.isBlank()) {
                task.setErrorMessage(truncate(errorMessage, 500));
            }
            taskRepository.save(task);
        });
    }

    /**
     * 重试前重置任务状态，保留已成功步骤结果
     */
    @Transactional
    public void prepareForRetry(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            List<Map<String, Object>> results = readStepResults(task.getStepResultsJson());
            List<Map<String, Object>> successful = results.stream()
                    .filter(m -> Boolean.TRUE.equals(m.get("success")))
                    .toList();
            try {
                task.setStepResultsJson(objectMapper.writeValueAsString(new ArrayList<>(successful)));
            } catch (JsonProcessingException e) {
                log.warn("[PipelineTask] failed to trim step results: {}", e.getMessage());
            }
            task.setStatus("RUNNING");
            task.setErrorMessage("");
            task.setCompletedSteps(successful.size());
            task.setCompletedAt(null);
            taskRepository.save(task);
        });
    }

    /**
     * 解析已存储的 Pipeline 配置
     */
    @Transactional(readOnly = true)
    public Optional<PipelineConfig> loadPipelineConfig(PipelineTask task) {
        if (task.getPipelineConfigJson() == null || task.getPipelineConfigJson().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(task.getPipelineConfigJson(), PipelineConfig.class));
        } catch (JsonProcessingException e) {
            log.warn("[PipelineTask] failed to deserialize config for {}: {}", task.getTaskId(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 从 step_results_json 恢复已成功步骤结果
     */
    @SuppressWarnings("unchecked")
    public List<StepResult> loadCompletedStepResults(PipelineTask task) {
        List<Map<String, Object>> raw = readStepResults(task.getStepResultsJson());
        List<StepResult> results = new ArrayList<>();
        for (Map<String, Object> m : raw) {
            if (!Boolean.TRUE.equals(m.get("success"))) {
                continue;
            }
            Map<String, Object> data = m.get("data") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : Map.of();
            long durationMs = m.containsKey("durationMs")
                    ? ((Number) m.get("durationMs")).longValue()
                    : 0L;
            results.add(StepResult.success(
                    String.valueOf(m.get("stepId")),
                    String.valueOf(m.get("agentId")),
                    data,
                    durationMs));
        }
        return results;
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
                        .errorMessage(m.containsKey("error") ? String.valueOf(m.get("error")) : null)
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
                .failedStep(task.getFailedStep())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .build();
    }

    // ===== 内部工具方法 =====

    private String serializeConfig(PipelineConfig config) {
        if (config == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.warn("[PipelineTask] failed to serialize pipeline config: {}", e.getMessage());
            return null;
        }
    }

    /** 去掉不可 JSON 序列化的值，避免污染 step_results_json */
    private Map<String, Object> sanitizeForJson(Map<String, Object> data) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(data),
                    new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException e) {
            Map<String, Object> safe = new LinkedHashMap<>();
            data.forEach((k, v) -> {
                if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                    safe.put(k, v);
                }
            });
            return safe;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readStepResults(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
