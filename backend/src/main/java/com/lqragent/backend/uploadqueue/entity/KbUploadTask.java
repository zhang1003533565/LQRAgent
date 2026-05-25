package com.lqragent.backend.uploadqueue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "kb_upload_task")
@Comment("知识库上传任务队列表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbUploadTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(nullable = false)
    @Comment("上传用户ID")
    private Long userId;

    @Column(nullable = false, length = 256)
    @Comment("原始文件名")
    private String fileName;

    @Column(nullable = false, length = 512)
    @Comment("临时存储路径或对象存储Key")
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    @Comment("知识库范围：PUBLIC公开/PERSONAL个人")
    private KbScope kbScope = KbScope.PERSONAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    @Comment("任务状态：PENDING/PROCESSING/COMPLETED/FAILED")
    private TaskStatus status = TaskStatus.PENDING;

    @Column
    @Builder.Default
    @Comment("优先级，数值越大越优先")
    private Integer priority = 0;

    @Column(length = 1024)
    @Comment("失败原因说明")
    private String errorMessage;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    @Comment("内容分析智能体输出（JSON）")
    private String analysisResult;

    @Column(name = "mapped_kp_ids", columnDefinition = "TEXT")
    @Comment("映射到的知识点ID列表（JSON数组）")
    private String mappedKpIds;

    @CreationTimestamp
    @Comment("任务创建时间")
    private LocalDateTime createdAt;

    @Comment("开始处理时间")
    private LocalDateTime startedAt;

    @Comment("处理完成时间")
    private LocalDateTime finishedAt;

    public enum KbScope {
        PUBLIC, PERSONAL
    }

    public enum TaskStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }
}
