package com.lqragent.backend.orchestrator.card;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.lqragent.backend.orchestrator.AgentIds;

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

    /**
     * 按能力标签精确匹配（capabilities 列表）
     */
    public List<AgentCard> findByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        String lower = tag.toLowerCase();
        return cards.values().stream()
                .filter(c -> c.capabilities() != null
                        && c.capabilities().stream().anyMatch(t -> t.equalsIgnoreCase(lower)))
                .collect(Collectors.toList());
    }

    /**
     * 按关键词匹配 agentId、展示名、描述、能力标签
     */
    public List<AgentCard> findByKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String lower = keyword.toLowerCase();
        return cards.values().stream()
                .filter(c -> matchesKeyword(c, lower))
                .collect(Collectors.toList());
    }

    /**
     * 按意图关键词匹配最佳 Agent（简单请求降级路由）
     */
    public String matchBestAgent(String keyword) {
        List<AgentCard> byTag = findByTag(keyword);
        if (!byTag.isEmpty()) {
            return byTag.get(0).agentId();
        }
        List<AgentCard> byKeyword = findByKeyword(keyword);
        if (!byKeyword.isEmpty()) {
            return byKeyword.get(0).agentId();
        }
        return AgentIds.QA;
    }

    /**
     * 构建用户帮助信息（HELP 意图）
     */
    public String buildHelpMessage() {
        if (cards.isEmpty()) {
            return "暂无可用能力，请稍后再试。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("我可以帮你做这些事情：\n");
        int idx = 1;
        for (AgentCard c : cards.values()) {
            sb.append(idx++).append(". ").append(c.description()).append("\n");
        }
        sb.append("\n请问有什么可以帮助你的？");
        return sb.toString();
    }

    private static boolean matchesKeyword(AgentCard card, String keyword) {
        if (card.agentId().toLowerCase().contains(keyword)) {
            return true;
        }
        if (card.displayName().toLowerCase().contains(keyword)) {
            return true;
        }
        if (card.description().toLowerCase().contains(keyword)) {
            return true;
        }
        if (card.capabilities() != null) {
            return card.capabilities().stream()
                    .anyMatch(t -> t.toLowerCase().contains(keyword));
        }
        return false;
    }
}
