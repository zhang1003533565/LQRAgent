package com.lqragent.backend.orchestrator.pipeline.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * Pipeline 任务持久化记录
 * 用于追踪每次 Pipeline 执行的状态、进度和结果
 */
@Entity
@Table(name = "pipeline_task")
@Comment("Pipeline 任务追踪表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "task_id", nullable = false, unique = true, length = 64)
    @Comment("任务唯一标识（UUID）")
    private String taskId;

    @Column(name = "user_id", nullable = false)
    @Comment("用户ID")
    private Long userId;

    @Column(name = "pipeline_id", nullable = false, length = 64)
    @Comment("Pipeline 模板ID，如 learning_path")
    private String pipelineId;

    @Column(name = "pipeline_name", length = 128)
    @Comment("Pipeline 名称")
    private String pipelineName;

    @Column(length = 512)
    @Comment("用户原始输入/学习目标")
    private String goal;

    @Column(length = 32)
    @Comment("任务状态：PENDING / RUNNING / COMPLETED / FAILED")
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "current_step", length = 64)
    @Comment("当前执行到的步骤ID")
    private String currentStep;

    @Column(name = "step_count")
    @Comment("总步骤数")
    @Builder.Default
    private Integer stepCount = 0;

    @Column(name = "completed_steps")
    @Comment("已完成步骤数")
    @Builder.Default
    private Integer completedSteps = 0;

    @Column(name = "step_results_json", columnDefinition = "TEXT")
    @Comment("各步骤执行结果摘要（JSON格式）")
    private String stepResultsJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Comment("失败原因")
    private String errorMessage;

    @Column(name = "duration_ms")
    @Comment("总耗时（毫秒）")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    @Comment("完成时间")
    private LocalDateTime completedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
