package com.lqragent.backend.agents.base;

import java.util.List;

/**
 * LLM 响应
 */
public record LlmResponse(String content, List<ToolCall> toolCalls, String error) {

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean isSuccess() {
        return error == null;
    }
}
