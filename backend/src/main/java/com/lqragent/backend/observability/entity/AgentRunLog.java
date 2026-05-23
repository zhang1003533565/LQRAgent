package com.lqragent.backend.observability.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_run_log")
@Comment("智能体运行日志表")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRunLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("主键ID")
    private Long id;

    @Column(name = "session_id", length = 64)
    @Comment("关联 chat_session.id")
    private String sessionId;

    @Column(name = "user_id", nullable = false)
    @Comment("学生用户ID")
    private Long userId;

    @Column(nullable = false, length = 32)
    @Comment("智能体标识")
    private String agent;

    @Column(length = 64)
    @Comment("协调智能体识别的意图")
    private String intent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    @Comment("RUNNING/SUCCESS/FAILED")
    private RunStatus status = RunStatus.RUNNING;

    @Column(name = "duration_ms")
    @Comment("执行耗时（毫秒）")
    private Integer durationMs;

    @Column(columnDefinition = "TEXT")
    @Comment("步骤详情（JSON）")
    private String detail;

    @Column(name = "error_message", length = 1024)
    @Comment("失败原因")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at")
    @Comment("记录时间")
    private LocalDateTime createdAt;

    public enum RunStatus {
        RUNNING, SUCCESS, FAILED
    }
}
