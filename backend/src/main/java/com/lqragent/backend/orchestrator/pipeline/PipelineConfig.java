package com.lqragent.backend.orchestrator.pipeline;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pipeline 配置
 * 定义完整的执行流程
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfig {
    
    /** Pipeline 唯一标识 */
    private String pipelineId;
    
    /** 名称 */
    private String name;
    
    /** 描述 */
    private String description;
    
    /** 步骤列表 */
    private List<PipelineStep> steps;
    
    /** 总超时时间（毫秒） */
    @Builder.Default
    private long totalTimeoutMs = 120000;
    
    /** 是否并行执行无依赖的步骤 */
    @Builder.Default
    private boolean parallel = true;
    
    /** 全局参数 */
    private java.util.Map<String, Object> globalParams;
}
