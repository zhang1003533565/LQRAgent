package com.lqragent.backend.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
@Comment("智能体交互对话历史表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "user_id", nullable = false)
    @Comment("学生用户ID")
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 64)
    @Comment("会话隔离ID")
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Comment("发送方：USER用户/AI智能体")
    private Sender sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 32)
    @Builder.Default
    @Comment("内容类型：TEXT纯文本/MULTI_CARD多模态卡片")
    private ContentType contentType = ContentType.TEXT;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    @Comment("消息主体（文本或多模态卡片JSON）")
    private String body;

    @CreationTimestamp
    @Column(name = "created_at")
    @Comment("消息创建时间")
    private LocalDateTime createdAt;

    public enum Sender {
        USER, AI
    }

    public enum ContentType {
        TEXT, MULTI_CARD
    }
}
