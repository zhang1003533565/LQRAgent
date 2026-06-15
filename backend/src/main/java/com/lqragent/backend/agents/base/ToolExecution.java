package com.lqragent.backend.agents.base;

import java.util.Map;

/**
 * 工具执行记录
 */
public record ToolExecution(
        String toolName,
        Map<String, Object> args,
        AgentTool.ToolResult result
) {}
