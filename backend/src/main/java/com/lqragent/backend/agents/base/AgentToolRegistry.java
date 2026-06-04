package com.lqragent.backend.agents.base;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册表
 * 管理所有可用的 Agent 工具
 */
@Slf4j
@Component("agentToolRegistry")
public class AgentToolRegistry {
    
    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();
    
    /**
     * 注册工具
     */
    public void register(AgentTool tool) {
        tools.put(tool.name(), tool);
        log.debug("[ToolRegistry] registered: {}", tool.name());
    }
    
    /**
     * 批量注册
     */
    public void registerAll(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            register(tool);
        }
    }
    
    /**
     * 获取工具
     */
    public AgentTool get(String name) {
        return tools.get(name);
    }
    
    /**
     * 获取所有工具名称
     */
    public Set<String> listNames() {
        return Collections.unmodifiableSet(tools.keySet());
    }
    
    /**
     * 获取工具列表（用于传给 LLM）
     */
    public List<Map<String, Object>> getToolSchemas() {
        List<Map<String, Object>> schemas = new ArrayList<>();
        for (AgentTool tool : tools.values()) {
            schemas.add(Map.of(
                "type", "function",
                "function", Map.of(
                    "name", tool.name(),
                    "description", tool.description(),
                    "parameters", tool.parameterSchema()
                )
            ));
        }
        return schemas;
    }
    
    /**
     * 执行工具
     */
    public AgentTool.ToolResult execute(String name, Map<String, Object> args) {
        AgentTool tool = tools.get(name);
        if (tool == null) {
            return AgentTool.ToolResult.failure("Tool not found: " + name);
        }
        try {
            return tool.execute(args);
        } catch (Exception e) {
            log.error("[ToolRegistry] execute failed: {}", name, e);
            return AgentTool.ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }
}
