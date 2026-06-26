package com.lqragent.backend.orchestrator.consultation.entity;

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

@Entity
@Table(name = "agent_consultation_log")
@Comment("Agent 协商 transcript 持久化（Phase 3）")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConsultationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 64)
    @Comment("chat_session.id")
    private String sessionId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 64)
    private String scene;

    @Column(name = "stop_reason", length = 32)
    private String stopReason;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "transcript_json", columnDefinition = "TEXT")
    private String transcriptJson;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
