package com.lqragent.backend.agents.resource_generation.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "resource_item")
@Comment("学习资源内容表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "kp_id", nullable = false, length = 64)
    @Comment("关联知识点ID")
    private String kpId;

    @Column(name = "resource_type", nullable = false, length = 32)
    @Comment("资源类型：LESSON/QUIZ/CODE_CASE/ILLUSTRATION/VIDEO_CLIP")
    private String resourceType;

    @Column(length = 256)
    @Comment("资源标题")
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    @Comment("文本资源内容（Markdown或JSON）")
    private String content;

    @Column(name = "media_url", length = 1024)
    @Comment("媒体资源地址（生图/生视频结果）")
    private String mediaUrl;

    @Column(name = "media_mime", length = 64)
    @Comment("媒体 MIME：image/png、video/mp4")
    private String mediaMime;

    @Column(name = "generation_prompt", columnDefinition = "TEXT")
    @Comment("生图/生视频所用提示词")
    private String generationPrompt;

    @Column(name = "quality_score")
    @Comment("质量评分：0-100")
    private Integer qualityScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /** 资源类型常量 */
    public static final String TYPE_LESSON = "LESSON";
    public static final String TYPE_QUIZ = "QUIZ";
    public static final String TYPE_CODE_CASE = "CODE_CASE";
    public static final String TYPE_ILLUSTRATION = "ILLUSTRATION";
    public static final String TYPE_MIND_MAP = "MIND_MAP";
    public static final String TYPE_EXTENDED_READING = "EXTENDED_READING";
    public static final String TYPE_SUMMARY = "SUMMARY";
    public static final String TYPE_VIDEO_CLIP = "VIDEO_CLIP";
}
