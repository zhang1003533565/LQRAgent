package com.lqragent.backend.orchestrator.card;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent Card 注册中心（替代 CapabilityRegistry）
 * <p>
 * 由 BaseAgent.start() 自动调用 register() 完成注册。
 * 供 PlanningAgent v2 通过 buildCatalog() 构建 LLM 能力目录。
 */
@Slf4j
@Service
public class AgentCardRegistry {

    private final Map<String, AgentCard> cards = new ConcurrentHashMap<>();

    public void register(AgentCard card) {
        cards.put(card.agentId(), card);
        log.info("[AgentCardRegistry] registered: {} (caps={}, outputs={})",
                card.agentId(), card.capabilities(), card.outputArtifactKinds());
    }

    public Optional<AgentCard> findById(String agentId) {
        return Optional.ofNullable(cards.get(agentId));
    }

    public Collection<AgentCard> getAll() {
        return cards.values();
    }

    public Set<String> getAllAgentIds() {
        return cards.keySet();
    }

    public int size() {
        return cards.size();
    }

    public boolean exists(String agentId) {
        return cards.containsKey(agentId);
    }

    /**
     * 构建 LLM 可读的能力目录（供 PlanningAgent v2 用）
     */
    public String buildCatalog() {
        if (cards.isEmpty()) return "暂无可用 Agent";
        StringBuilder sb = new StringBuilder();
        sb.append("可用 Agent 及其能力：\n");
        for (AgentCard c : cards.values()) {
            sb.append("- ").append(c.agentId())
                    .append("：").append(c.description());
            if (c.capabilities() != null && !c.capabilities().isEmpty()) {
                sb.append(" 标签：").append(String.join(",", c.capabilities()));
            }
            if (c.outputArtifactKinds() != null && !c.outputArtifactKinds().isEmpty()) {
                sb.append(" 产出：").append(String.join(",", c.outputArtifactKinds()));
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
