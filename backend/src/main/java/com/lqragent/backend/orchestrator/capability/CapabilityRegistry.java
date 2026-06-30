package com.lqragent.backend.orchestrator.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Agent 能力注册表（CFP 协商协议使用）。
 * 目前为最小实现，后续可从 AgentCard 或配置中自动加载能力元数据。
 */
@Component
public class CapabilityRegistry {

    private final ConcurrentHashMap<String, AgentCapability> capabilities = new ConcurrentHashMap<>();

    public void register(AgentCapability capability) {
        if (capability != null && capability.agentId() != null) {
            capabilities.put(capability.agentId(), capability);
        }
    }

    public Optional<AgentCapability> findById(String agentId) {
        return Optional.ofNullable(capabilities.get(agentId));
    }

    public List<AgentCapability> findByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String lower = keyword.toLowerCase();
        List<AgentCapability> result = new ArrayList<>();
        for (AgentCapability cap : capabilities.values()) {
            if (cap.description() != null && cap.description().toLowerCase().contains(lower)) {
                result.add(cap);
                continue;
            }
            if (cap.tags() != null && cap.tags().stream().anyMatch(t -> t.toLowerCase().contains(lower))) {
                result.add(cap);
            }
        }
        return result;
    }
}
