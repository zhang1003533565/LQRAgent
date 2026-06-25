package com.lqragent.backend.agents.base;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 工具调用
 */
public record ToolCall(String id, String name, String argumentsJson) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {};

    public Map<String, Object> parseArguments() {
        try {
            return MAPPER.readValue(argumentsJson, ARGUMENTS_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
