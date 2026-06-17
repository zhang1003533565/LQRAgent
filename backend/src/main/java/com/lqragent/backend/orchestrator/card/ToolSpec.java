package com.lqragent.backend.orchestrator.card;

import java.util.Map;

/**
 * 工具规格说明
 */
public record ToolSpec(
        String name,
        String description,
        Map<String, Object> parameterSchema
) {
    public static ToolSpec of(String name, String description) {
        return new ToolSpec(name, description, Map.of("type", "object"));
    }
}
