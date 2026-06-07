package com.lqragent.backend.chat.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

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

/**
 * 聊天消息实体
 */
@Entity
@Table(name = "chat_message")
@Comment("聊天消息表")
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

    @Column(name = "session_id", nullable = false)
    @Comment("会话ID")
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    @Comment("用户ID")
    private Long userId;

    @Column(nullable = false, length = 20)
    @Comment("角色：user/assistant/system")
    private String role;

    @Column(columnDefinition = "TEXT")
    @Comment("消息内容")
    private String content;

    @Column(name = "content_type", length = 20)
    @Builder.Default
    @Comment("内容类型：text/multi_card/diagram")
    private String contentType = "text";

    @Column(name = "agent_name", length = 50)
    @Comment("处理该消息的Agent")
    private String agentName;

    @Column(columnDefinition = "JSON")
    @Comment("附加信息（RAG源、图表代码等）")
    private String metadata;

    @CreationTimestamp
    @Column(name = "created_at")
    @Comment("创建时间")
    private LocalDateTime createdAt;
}
