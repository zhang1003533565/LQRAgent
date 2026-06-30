package com.lqragent.backend.orchestrator.capability;

import java.util.List;

/**
 * Agent 能力描述（供 CapabilityRegistry 使用）。
 */
public record AgentCapability(
        String agentId,
        String description,
        List<String> tags,
        long avgLatencyMs
) {
}
