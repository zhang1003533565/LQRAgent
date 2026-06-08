package com.lqragent.backend.orchestrator.capability;

import java.util.List;
import java.util.Set;

/**
 * Agent 能力描述
 * 每个 Agent 向 CapabilityRegistry 注册自己的能力信息，
 * PlanningAgent 据此动态构建能力目录，实现 Agent 动态发现。
 *
 * @param agentId     Agent 唯一标识
 * @param displayName 展示名称
 * @param description 能力描述（供 LLM 理解）
 * @param actions     支持的操作列表
 * @param tags        能力标签（用于关键词匹配）
 * @param inputHint   期望输入格式提示
 * @param outputHint  输出格式提示
 * @param avgLatencyMs 平均延迟（毫秒）
 */
public record AgentCapability(
        String agentId,
        String displayName,
        String description,
        List<String> actions,
        Set<String> tags,
        String inputHint,
        String outputHint,
        long avgLatencyMs
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String agentId;
        private String displayName;
        private String description;
        private List<String> actions = List.of("process");
        private Set<String> tags = Set.of();
        private String inputHint = "";
        private String outputHint = "";
        private long avgLatencyMs = 30000;

        public Builder agentId(String v) { this.agentId = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder actions(List<String> v) { this.actions = v; return this; }
        public Builder tags(Set<String> v) { this.tags = v; return this; }
        public Builder inputHint(String v) { this.inputHint = v; return this; }
        public Builder outputHint(String v) { this.outputHint = v; return this; }
        public Builder avgLatencyMs(long v) { this.avgLatencyMs = v; return this; }

        public AgentCapability build() {
            if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId required");
            if (displayName == null) displayName = agentId;
            if (description == null) description = "";
            return new AgentCapability(agentId, displayName, description, actions, tags, inputHint, outputHint, avgLatencyMs);
        }
    }
}
