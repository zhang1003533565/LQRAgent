package com.lqragent.backend.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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
    @Column(length = 64)
    @Comment("会话ID（UUID）")
    private String id;

    @Column(name = "user_id", nullable = false)
    @Comment("学生用户ID")
    private Long userId;

    @Column(length = 256)
    @Comment("会话标题")
    private String title;

    @Column(name = "ai_server_session_id", length = 128)
    @Comment("ai-server 侧会话ID")
    private String aiServerSessionId;

    @CreationTimestamp
    @Column(name = "created_at")
    @Comment("创建时间")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Comment("更新时间")
    private LocalDateTime updatedAt;
}
