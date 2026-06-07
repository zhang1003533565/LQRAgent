package com.lqragent.backend.chat.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

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

    // ====== 新增：链路追踪字段 ======

    @Column(name = "trace_id", length = 64)
    @Comment("链路ID（同一任务共享）")
    private String traceId;

    @Column(name = "span_id", length = 64)
    @Comment("当前跨度ID")
    private String spanId;

    @Column(name = "parent_span_id", length = 64)
    @Comment("父跨度ID")
    private String parentSpanId;

    @Column(name = "input_summary", length = 500)
    @Comment("输入摘要")
    private String inputSummary;

    @Column(name = "output_summary", length = 500)
    @Comment("输出摘要")
    private String outputSummary;

    public enum RunStatus {
        RUNNING, SUCCESS, FAILED
    }

    public static AgentRunLog of(String sessionId, String agent, String input,
                                  String output, boolean success, String errorMessage,
                                  long durationMs) {
        return AgentRunLog.builder()
                .sessionId(sessionId)
                .agent(agent)
                .detail(output)
                .status(success ? RunStatus.SUCCESS : RunStatus.FAILED)
                .errorMessage(errorMessage)
                .durationMs((int) durationMs)
                .build();
    }
}
