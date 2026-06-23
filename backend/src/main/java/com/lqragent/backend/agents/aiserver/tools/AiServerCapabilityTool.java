package com.lqragent.backend.agents.aiserver.tools;

import java.util.Map;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;

import lombok.extern.slf4j.Slf4j;

/**
 * ai-server capability 统一工具封装。
 */
@Slf4j
public class AiServerCapabilityTool implements AgentTool {

    private final AiServerWsProxy proxy;
    private final String capabilityName;
    private final String toolName;
    private final String toolDescription;
    private final Map<String, Object> schema;

    public AiServerCapabilityTool(AiServerWsProxy proxy, String capabilityName,
                                  String toolName, String toolDescription,
                                  Map<String, Object> schema) {
        this.proxy = proxy;
        this.capabilityName = capabilityName;
        this.toolName = toolName;
        this.toolDescription = toolDescription;
        this.schema = schema;
    }

    @Override
    public String name() {
        return toolName;
    }

    @Override
    public String description() {
        return toolDescription;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return schema;
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String result = proxy.callCapability(capabilityName, args);
            if (result == null || result.isBlank()) {
                return ToolResult.failure("ai-server " + capabilityName + " 返回空");
            }
            return ToolResult.success(result, Map.of("capability", capabilityName));
        } catch (Exception e) {
            log.error("[AiServerCapabilityTool] {} 调用失败: {}", capabilityName, e.getMessage());
            return ToolResult.failure("ai-server 调用失败：" + e.getMessage());
        }
    }
}
