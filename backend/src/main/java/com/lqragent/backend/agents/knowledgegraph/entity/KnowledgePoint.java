package com.lqragent.backend.agents.knowledgegraph.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_point")
@Comment("知识点表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KnowledgePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "kp_id", nullable = false, unique = true, length = 64)
    @Comment("知识点唯一标识，如 kp_decorator")
    private String kpId;

    @Column(nullable = false, length = 128)
    @Comment("知识点名称")
    private String title;

    @Column(columnDefinition = "TEXT")
    @Comment("知识点描述")
    private String description;

    @Column(length = 64)
    @Comment("所属章节")
    private String chapter;

    @Column(length = 64)
    @Comment("所属学科/科目，如 Python基础、数据结构、算法")
    @Builder.Default
    private String subject = "默认";

    @Column(nullable = false)
    @Comment("难度等级：1-5")
    private Integer difficulty;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
