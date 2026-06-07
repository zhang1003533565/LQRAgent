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
 * 聊天会话实体
 */
@Entity
@Table(name = "chat_session")
@Comment("聊天会话表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("用户ID")
    private Long userId;

    @Column(length = 200)
    @Comment("会话标题")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    @Comment("会话状态")
    private SessionStatus status = SessionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at")
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Comment("更新时间")
    private LocalDateTime updatedAt;

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        ACTIVE,    // 活跃
        ARCHIVED,  // 已归档
        DELETED    // 已删除
    }
}
