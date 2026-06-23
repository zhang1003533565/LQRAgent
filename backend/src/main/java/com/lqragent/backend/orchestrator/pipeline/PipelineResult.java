package com.lqragent.backend.orchestrator.pipeline;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pipeline 执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResult {
    
    /** 是否成功 */
    private boolean success;
    
    /** 所有步骤的执行结果 */
    private List<StepResult> stepResults;
    
    /** 最终输出（最后一个步骤的结果） */
    private Map<String, Object> output;
    
    /** 错误信息 */
    private String errorMessage;
    
    /** 总耗时（毫秒） */
    private long totalDurationMs;
    
    /**
     * 创建成功结果
     */
    public static PipelineResult success(List<StepResult> stepResults, 
                                          Map<String, Object> output, 
                                          long totalDurationMs) {
        return PipelineResult.builder()
                .success(true)
                .stepResults(stepResults)
                .output(output)
                .totalDurationMs(totalDurationMs)
                .build();
    }
    
    /**
     * 创建失败结果
     */
    public static PipelineResult failure(String errorMessage, long totalDurationMs) {
        return failure(errorMessage, List.of(), totalDurationMs);
    }

    /**
     * 创建失败结果（保留已完成步骤，便于提取 artifact）
     */
    public static PipelineResult failure(String errorMessage, List<StepResult> stepResults,
                                         long totalDurationMs) {
        return PipelineResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .stepResults(stepResults != null ? stepResults : List.of())
                .totalDurationMs(totalDurationMs)
                .build();
    }
}
