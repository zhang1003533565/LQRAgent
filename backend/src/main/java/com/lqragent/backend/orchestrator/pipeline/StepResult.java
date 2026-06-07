package com.lqragent.backend.orchestrator.pipeline;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单个步骤的执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {
    
    /** 步骤ID */
    private String stepId;
    
    /** Agent ID */
    private String agentId;
    
    /** 是否成功 */
    private boolean success;
    
    /** 执行状态 */
    private StepStatus status;
    
    /** 结果数据 */
    private Map<String, Object> data;
    
    /** 错误信息 */
    private String errorMessage;
    
    /** 执行耗时（毫秒） */
    private long durationMs;
    
    /** 重试次数 */
    private int retryCount;
    
    public enum StepStatus {
        PENDING, RUNNING, SUCCESS, FAILED, SKIPPED, TIMEOUT
    }
    
    /**
     * 创建成功结果
     */
    public static StepResult success(String stepId, String agentId, 
                                      Map<String, Object> data, long durationMs) {
        return StepResult.builder()
                .stepId(stepId)
                .agentId(agentId)
                .success(true)
                .status(StepStatus.SUCCESS)
                .data(data)
                .durationMs(durationMs)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static StepResult failure(String stepId, String agentId, 
                                      String errorMessage, long durationMs) {
        return StepResult.builder()
                .stepId(stepId)
                .agentId(agentId)
                .success(false)
                .status(StepStatus.FAILED)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .build();
    }
    
    /**
     * 创建跳过结果
     */
    public static StepResult skipped(String stepId, String agentId, Exception e) {
        return StepResult.builder()
                .stepId(stepId)
                .agentId(agentId)
                .success(false)
                .status(StepStatus.SKIPPED)
                .errorMessage(e != null ? e.getMessage() : "Skipped")
                .build();
    }
}
