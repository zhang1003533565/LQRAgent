package com.lqragent.backend.agents.base;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent 注册中心
 * 维护所有 Agent 实例，支持按 ID 查找
 * Agent 之间通过此注册中心实现相互调用
 */
@Slf4j
@Service
public class AgentRegistry {
    
    private final Map<String, AgentInterface> agents = new ConcurrentHashMap<>();
    
    /**
     * 注册 Agent
     */
    public void register(AgentInterface agent) {
        agents.put(agent.getAgentId(), agent);
        log.info("[AgentRegistry] registered: {}", agent.getAgentId());
    }
    
    /**
     * 批量注册
     */
    public void registerAll(Collection<AgentInterface> agents) {
        agents.forEach(this::register);
    }
    
    /**
     * 获取 Agent
     */
    public Optional<AgentInterface> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }
    
    /**
     * 获取所有已注册 Agent
     */
    public Collection<AgentInterface> getAllAgents() {
        return agents.values();
    }
    
    /**
     * 检查 Agent 是否存在
     */
    public boolean hasAgent(String agentId) {
        return agents.containsKey(agentId);
    }
    
    /**
     * 获取注册数量
     */
    public int size() {
        return agents.size();
    }

    /**
     * 获取所有已注册 Agent ID
     */
    public Set<String> getAllAgentIds() {
        return Collections.unmodifiableSet(agents.keySet());
    }
}
