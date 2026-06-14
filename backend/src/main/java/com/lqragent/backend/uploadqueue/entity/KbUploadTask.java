package com.lqragent.backend.uploadqueue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
    @Comment("对象存储 key")
    private String filePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    @Comment("知识库范围：PUBLIC/PERSONAL")
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

    @Column(name = "status_message", length = 512)
    @Comment("任务状态提示信息")
    private String statusMessage;

    @Column(name = "progress_percent")
    @Comment("任务进度百分比")
    private Integer progressPercent;

    @Column(name = "analysis_result", columnDefinition = "TEXT")
    @Comment("内容分析结果 JSON")
    private String analysisResult;

    @Column(name = "mapped_kp_ids", columnDefinition = "TEXT")
    @Comment("映射到的知识点 ID 列表")
    private String mappedKpIds;

    @Column(name = "vector_chunk_count")
    @Comment("向量化切分后的块数量")
    private Integer vectorChunkCount;

    @Column(name = "vector_total_tokens")
    @Comment("向量化总 token 数")
    private Long vectorTotalTokens;

    @Column(name = "vector_index_name", length = 256)
    @Comment("向量索引名称")
    private String vectorIndexName;

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
