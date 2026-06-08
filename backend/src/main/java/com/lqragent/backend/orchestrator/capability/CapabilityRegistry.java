package com.lqragent.backend.orchestrator.capability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Agent 能力注册中心
 * <p>
 * 职责：
 * 1. 维护所有 Agent 的能力注册信息
 * 2. 提供能力发现（按 ID、标签、关键词）
 * 3. 为 PlanningAgent 构建动态 LLM 能力目录
 * 4. 支持 Agent 间协商时的能力查询
 */
@Slf4j
@Service
public class CapabilityRegistry {

    private final Map<String, AgentCapability> registry = new ConcurrentHashMap<>();

    /**
     * 注册 Agent 能力
     */
    public void register(AgentCapability capability) {
        registry.put(capability.agentId(), capability);
        log.info("[CapabilityRegistry] registered: {} (actions={}, tags={})",
                capability.agentId(), capability.actions(), capability.tags());
    }

    /**
     * 批量注册
     */
    public void registerAll(Collection<AgentCapability> capabilities) {
        capabilities.forEach(this::register);
    }

    /**
     * 注销 Agent 能力
     */
    public void unregister(String agentId) {
        registry.remove(agentId);
        log.info("[CapabilityRegistry] unregistered: {}", agentId);
    }

    /**
     * 获取指定 Agent 的能力
     */
    public Optional<AgentCapability> findById(String agentId) {
        return Optional.ofNullable(registry.get(agentId));
    }

    /**
     * 按标签查找 Agent
     */
    public List<AgentCapability> findByTag(String tag) {
        return registry.values().stream()
                .filter(c -> c.tags().contains(tag))
                .collect(Collectors.toList());
    }

    /**
     * 按关键词查找（匹配 agentId、displayName、description、tags）
     */
    public List<AgentCapability> findByKeyword(String keyword) {
        String lower = keyword.toLowerCase();
        return registry.values().stream()
                .filter(c -> matchesKeyword(c, lower))
                .collect(Collectors.toList());
    }

    private boolean matchesKeyword(AgentCapability c, String keyword) {
        if (c.agentId().toLowerCase().contains(keyword)) return true;
        if (c.displayName().toLowerCase().contains(keyword)) return true;
        if (c.description().toLowerCase().contains(keyword)) return true;
        return c.tags().stream().anyMatch(t -> t.toLowerCase().contains(keyword));
    }

    /**
     * 获取所有已注册 Agent ID
     */
    public Set<String> getAllAgentIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * 获取所有能力
     */
    public Collection<AgentCapability> getAllCapabilities() {
        return Collections.unmodifiableCollection(registry.values());
    }

    /**
     * 构建 LLM 可读的能力目录描述（供 PlanningAgent 使用）
     */
    public String buildCapabilityCatalog() {
        if (registry.isEmpty()) {
            return "暂无可用 Agent";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("可用 Agent 及其能力：\n");
        for (AgentCapability c : registry.values()) {
            sb.append("- ").append(c.agentId());
            sb.append(": ").append(c.description());
            if (!c.actions().isEmpty() && !c.actions().equals(List.of("process"))) {
                sb.append(" 支持操作: ").append(String.join(", ", c.actions()));
            }
            if (!c.tags().isEmpty()) {
                sb.append(" 标签: ").append(String.join(", ", c.tags()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 按意图关键词匹配最佳 Agent（用于降级场景）
     *
     * @param keyword 意图关键词
     * @return 匹配的 agentId，无匹配返回 "qa_agent"
     */
    public String matchBestAgent(String keyword) {
        // 优先按标签精确匹配
        List<AgentCapability> byTag = findByTag(keyword);
        if (!byTag.isEmpty()) {
            return byTag.get(0).agentId();
        }

        // 按关键词模糊匹配
        List<AgentCapability> byKeyword = findByKeyword(keyword);
        if (!byKeyword.isEmpty()) {
            return byKeyword.get(0).agentId();
        }

        // 无匹配时走 QA
        return "qa_agent";
    }

    /**
     * 获取注册的 Agent 数量
     */
    public int size() {
        return registry.size();
    }
}
