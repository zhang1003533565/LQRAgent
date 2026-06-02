package com.lqragent.backend.framework;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一智能体处理结果。
 * 每个 Agent.process() 必须返回此类型。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResult {

    /** 对应 AgentTask.taskId */
    private String taskId;

    /** 来源智能体 */
    private String agentType;

    /** 是否成功 */
    @Builder.Default
    private boolean success = true;

    /** 业务结果数据 */
    @Builder.Default
    private Map<String, Object> data = new HashMap<>();

    /** 失败原因 */
    private String errorMessage;

    /** 处理耗时（毫秒） */
    private long durationMs;
}
