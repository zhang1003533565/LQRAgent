package com.lqragent.backend.orchestrator.card;

import java.util.List;

/**
 * Agent 能力声明卡（借鉴 A2A Agent Card，简化版）
 * 替代 CapabilityRegistry，由 Agent 自己声明
 *
 * @param agentId            Agent ID（必须唯一）
 * @param displayName        中文展示名
 * @param description        能力描述（给 LLM 看）
 * @param capabilities       能力标签，供 LLM 规划检索
 * @param tools              可用工具
 * @param inputArtifactKinds 接受的输入 Artifact 类型
 * @param outputArtifactKinds 产出的 Artifact 类型
 * @param maxConcurrency     最大并发
 * @param avgLatencyMs       平均延迟（毫秒）
 */
public record AgentCard(
        String agentId,
        String displayName,
        String description,
        List<String> capabilities,
        List<ToolSpec> tools,
        List<String> inputArtifactKinds,
        List<String> outputArtifactKinds,
        int maxConcurrency,
        long avgLatencyMs
) {
    public static AgentCard simple(String agentId, String displayName, String description,
                                   List<String> capabilities, List<String> outputs) {
        return new AgentCard(agentId, displayName, description, capabilities,
                List.of(), List.of(), outputs, 1, 30000L);
    }
}
