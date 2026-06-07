package com.lqragent.backend.orchestrator.context;

import java.time.Instant;

import lombok.Data;

/**
 * 链路追踪跨度
 * 记录每个 Agent 调用的执行信息
 */
@Data
public class TraceSpan {
    
    /** 追踪ID（同一任务共享） */
    private String traceId;
    
    /** 跨度ID（唯一） */
    private String spanId;
    
    /** 父跨度ID */
    private String parentSpanId;
    
    /** Agent ID */
    private String agentId;
    
    /** 步骤ID（Pipeline 中的步骤） */
    private String stepId;
    
    /** 状态 */
    private SpanStatus status = SpanStatus.RUNNING;
    
    /** 开始时间 */
    private Instant startTime;
    
    /** 结束时间 */
    private Instant endTime;
    
    /** 执行耗时（毫秒） */
    private Long durationMs;
    
    /** 输入摘要 */
    private String inputSummary;
    
    /** 输出摘要 */
    private String outputSummary;
    
    /** 错误信息 */
    private String errorMessage;
    
    public TraceSpan(String stepId, String agentId) {
        this.spanId = generateSpanId();
        this.stepId = stepId;
        this.agentId = agentId;
        this.startTime = Instant.now();
    }
    
    public TraceSpan(String traceId, String stepId, String agentId) {
        this(stepId, agentId);
        this.traceId = traceId;
    }
    
    /**
     * 标记完成
     */
    public void complete(String outputSummary) {
        this.endTime = Instant.now();
        this.status = SpanStatus.SUCCESS;
        this.outputSummary = outputSummary;
        this.durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * 标记失败
     */
    public void fail(String errorMessage) {
        this.endTime = Instant.now();
        this.status = SpanStatus.ERROR;
        this.errorMessage = errorMessage;
        this.durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
    }
    
    /**
     * 设置错误（兼容旧调用）
     */
    public void setError(Exception e) {
        fail(e.getMessage());
    }
    
    private static String generateSpanId() {
        return "span-" + System.nanoTime() + "-" + (int) (Math.random() * 1000);
    }
    
    public enum SpanStatus {
        RUNNING, SUCCESS, ERROR, TIMEOUT
    }
}
