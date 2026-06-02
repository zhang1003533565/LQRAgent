package com.lqragent.backend.core.tool;

import java.util.List;
import java.util.Map;

/**
 * Tool Schema — 描述一个 LLM 可调用的工具。
 * 序列化为 OpenAI function calling 格式。
 */
public record ToolSchema(
    String name,
    String description,
    Map<String, Object> parameters
) {
    /** 转成 OpenAI API 的 tool 格式 */
    public Map<String, Object> toOpenAISpec() {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", name,
                "description", description,
                "parameters", parameters
            )
        );
    }

    // ========== 便捷工厂 ==========

    /** 创建一个无参数的 tool */
    public static ToolSchema of(String name, String description) {
        return new ToolSchema(name, description, Map.of(
            "type", "object",
            "properties", Map.of()
        ));
    }

    /** 创建一个有参数的 tool */
    public static ToolSchema of(String name, String description, Map<String, Object> parameters) {
        return new ToolSchema(name, description, parameters);
    }

    /** 构建字符串参数 */
    public static Map<String, Object> stringParam(String name, String description) {
        return Map.of("type", "string", "description", description);
    }

    /** 构建 JSON Schema 的 parameters 对象 */
    public static Map<String, Object> params(Map<String, Object> properties, String... required) {
        return Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of(required)
        );
    }
}
