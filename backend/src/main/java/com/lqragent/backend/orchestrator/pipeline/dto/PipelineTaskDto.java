package com.lqragent.backend.orchestrator.pipeline.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Pipeline 任务状态 DTO
 * 用于前端查询任务进度
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineTaskDto {

    private Long id;
    private String taskId;
    private Long userId;
    private String pipelineId;
    private String pipelineName;
    private String goal;
    private String status;
    private String currentStep;
    private Integer stepCount;
    private Integer completedSteps;
    private List<StepProgress> stepProgressList;
    private String errorMessage;
    private String failedStep;
    private Long durationMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepProgress {
        private String stepId;
        private String agentId;
        private String action;
        private String stepName;
        private String status;
        private String errorMessage;
        private Long durationMs;
    }
}
