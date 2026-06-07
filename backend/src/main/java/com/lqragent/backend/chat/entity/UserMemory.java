package com.lqragent.backend.chat.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

/**
 * 用户长期记忆实体
 */
@Entity
@Table(name = "user_memory")
@Comment("用户长期记忆表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("用户ID")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "memory_type", nullable = false, length = 30)
    @Comment("记忆类型")
    private MemoryType memoryType;

    @Column(columnDefinition = "TEXT")
    @Comment("记忆内容")
    private String content;

    @Column(length = 50)
    @Comment("来源：auto_extract/user_setting/agent_update")
    private String source;

    @Column(nullable = false)
    @Builder.Default
    @Comment("重要程度 1-5")
    private Integer importance = 1;

    @Column(name = "access_count")
    @Builder.Default
    @Comment("访问次数")
    private Integer accessCount = 0;

    @Column(name = "last_accessed_at")
    @Comment("最后访问时间")
    private LocalDateTime lastAccessedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    /**
     * 记忆类型枚举
     */
    public enum MemoryType {
        PREFERENCE,        // 用户偏好
        LEARNING_PROGRESS, // 学习进度
        TOPIC_INTEREST,    // 话题兴趣
        INTERACTION_STYLE, // 交互风格
        KNOWLEDGE_STATE    // 知识掌握状态
    }
}
