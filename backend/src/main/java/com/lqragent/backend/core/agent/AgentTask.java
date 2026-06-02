package com.lqragent.backend.core.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一智能体任务。
 * 由 Orchestrator 创建，经 AgentBus 派发至目标 Agent。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTask {

    /** 全局唯一任务 ID */
    private String taskId;

    /** 请求链路 ID，用于追踪一次用户请求经过的多个 agent */
    private String requestId;

    /** 用户 ID */
    private Long userId;

    /** 会话 ID（WS 场景） */
    private String sessionId;

    /** 目标智能体标识，对应 Agent.agentId() */
    private String agentType;

    /** Orchestrator 识别的意图 */
    private String intent;

    /** 业务载荷 */
    @Builder.Default
    private Map<String, Object> payload = new HashMap<>();

    /** 当前重试次数 */
    @Builder.Default
    private int retryCount = 0;

    /** 最大重试次数 */
    @Builder.Default
    private int maxRetries = 1;
}
