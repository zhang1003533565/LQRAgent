package com.lqragent.backend.agents.base;

import java.util.Map;

/**
 * Agent 请求
 */
public record AgentRequest(
        String action,
        String goal,
        Map<String, Object> context
) {}
