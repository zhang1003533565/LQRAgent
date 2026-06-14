package com.lqragent.backend.uploadqueue.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
@Table(name = "vector_chunk", indexes = {
        @Index(name = "idx_chunk_task_id", columnList = "task_id"),
        @Index(name = "idx_chunk_index_name", columnList = "index_name"),
        @Index(name = "idx_chunk_kp_id", columnList = "kp_id")
})
@Comment("向量切块表 - 存储文档向量化后的每个文本块")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VectorChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "task_id", nullable = false)
    @Comment("关联的上传任务ID")
    private Long taskId;

    @Column(name = "index_name", length = 256)
    @Comment("向量索引名称")
    private String indexName;

    @Column(name = "chunk_index")
    @Comment("块序号（在文档中的顺序）")
    private Integer chunkIndex;

    @Column(name = "content", columnDefinition = "TEXT")
    @Comment("切分后的文本内容")
    private String content;

    @Column(name = "token_count")
    @Comment("该块的 token 数量")
    private Integer tokenCount;

    @Column(name = "start_pos")
    @Comment("在原始文档中的起始位置")
    private Integer startPos;

    @Column(name = "end_pos")
    @Comment("在原始文档中的结束位置")
    private Integer endPos;

    @Column(name = "kp_id", length = 64)
    @Comment("关联的知识点ID")
    private String kpId;

    @Column(name = "metadata", columnDefinition = "TEXT")
    @Comment("元数据 JSON")
    private String metadata;

    @CreationTimestamp
    @Comment("创建时间")
    private LocalDateTime createdAt;
}