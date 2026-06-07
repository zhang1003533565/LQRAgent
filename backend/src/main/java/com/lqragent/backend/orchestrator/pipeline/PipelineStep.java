package com.lqragent.backend.orchestrator.pipeline;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pipeline 步骤定义
 * 描述单个 Agent 任务的执行配置
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStep {
    
    /** 步骤ID（唯一标识） */
    private String stepId;
    
    /** 目标 Agent ID */
    private String agentId;
    
    /** 执行的动作 */
    private String action;
    
    /** 参数 */
    private Map<String, Object> params;
    
    /** 依赖的 stepId 列表 */
    @Builder.Default
    private List<String> dependsOn = List.of();
    
    /** 条件类型 */
    @Builder.Default
    private ConditionType conditionType = ConditionType.ALL_COMPLETED;
    
    /** 最大重试次数 */
    @Builder.Default
    private int maxRetries = 3;
    
    /** 超时时间（毫秒） */
    @Builder.Default
    private long timeoutMs = 30000;
    
    /** 是否可选步骤（失败不阻断） */
    @Builder.Default
    private boolean optional = false;
    
    /** 从依赖步骤结果中提取的参数映射 */
    private Map<String, String> resultMapping;
    
    public enum ConditionType {
        ALL_COMPLETED,   // 所有依赖完成
        ANY_COMPLETED    // 任一依赖完成
    }
}
