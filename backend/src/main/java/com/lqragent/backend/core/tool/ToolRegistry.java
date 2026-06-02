package com.lqragent.backend.core.tool;

import com.lqragent.backend.core.agent.Agent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心 — 取代旧 AgentBus 的通信职能。
 * <p>
 * 每个 Agent 在初始化时调用 register() 注册其所有 Tool，
 * AgentEngine 在推理循环中通过此注册表查找并执行工具。
 * </p>
 */
@Slf4j
@Component
public class ToolRegistry {

    private final List<Agent> agentList;
    private final Map<String, Map<String, ToolExecutor>> tools = new ConcurrentHashMap<>();
    private final Map<String, Agent> agentMap = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ToolRegistry(List<Agent> agentList) {
        this.agentList = agentList;
    }

    @PostConstruct
    void init() {
        for (Agent agent : agentList) {
            String id = agent.agentId();
            if (agentMap.putIfAbsent(id, agent) != null) {
                log.warn("[ToolRegistry] 重复注册 agentId={}", id);
                continue;
            }
            // Agent 注册工具（在 registerTools() 中调用 registry.register()）
            agent.registerTools(this);
            log.info("[ToolRegistry] 注册 agent: {} ({} tools)", id, agent.getTools().size());
        }
        log.info("[ToolRegistry] 已注册 {} 个智能体，总计 {} 个工具",
            agentMap.size(),
            tools.values().stream().mapToInt(Map::size).sum());
    }

    /** Agent 调用此方法注册工具 */
    public void register(String agentId, String toolName, ToolExecutor executor) {
        tools.computeIfAbsent(agentId, k -> new ConcurrentHashMap<>())
            .put(toolName, executor);
    }

    /** 执行指定 Agent 的某个工具 */
    public ToolResult execute(String agentId, String toolName, String argumentsJson) {
        Map<String, ToolExecutor> agentTools = tools.get(agentId);
        if (agentTools == null) {
            return ToolResult.fail(null, toolName, "未知 Agent: " + agentId);
        }
        ToolExecutor executor = agentTools.get(toolName);
        if (executor == null) {
            return ToolResult.fail(null, toolName, "未知工具: " + toolName);
        }
        try {
            Object result = executor.execute(argumentsJson);
            return ToolResult.ok(null, toolName, result);
        } catch (Exception e) {
            log.error("[ToolRegistry] 工具执行失败: agent={}, tool={}, err={}", agentId, toolName, e.getMessage());
            return ToolResult.fail(null, toolName, e.getMessage());
        }
    }

    /** 解析 JSON 参数 */
    public Map<String, Object> parseArgs(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    /** 获取 Agent */
    public Agent getAgent(String agentId) {
        return agentMap.get(agentId);
    }

    public List<String> listAgents() {
        return List.copyOf(agentMap.keySet());
    }

    public int agentCount() {
        return agentMap.size();
    }
}
