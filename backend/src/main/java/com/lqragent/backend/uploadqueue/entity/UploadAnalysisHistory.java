package com.lqragent.backend.uploadqueue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "upload_analysis_history")
@Comment("上传分析历史表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadAnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("用户ID")
    private Long userId;

    @Column(name = "upload_task_id")
    @Comment("关联上传任务ID")
    private Long uploadTaskId;

    @Column(name = "file_name", nullable = false, length = 256)
    @Comment("文件名")
    private String fileName;

    @Column(name = "file_path", length = 512)
    @Comment("文件存储路径或对象key")
    private String filePath;

    @Column(columnDefinition = "TEXT")
    @Comment("内容摘要")
    private String summary;

    @Column(name = "mapped_kp_ids", columnDefinition = "TEXT")
    @Comment("映射知识点ID列表（JSON数组）")
    private String mappedKpIds;

    @Column(name = "matched_knowledge_points", columnDefinition = "TEXT")
    @Comment("知识点匹配详情（JSON）")
    private String matchedKnowledgePoints;

    @Column(nullable = false, length = 16)
    @Comment("分析状态：COMPLETED/FAILED")
    private String status;

    @Column(name = "error_message", length = 1024)
    @Comment("失败原因")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @Column(name = "finished_at")
    @Comment("分析完成时间")
    private LocalDateTime finishedAt;
}
