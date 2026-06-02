package com.lqragent.backend.core.tool;

/**
 * 工具执行结果。
 */
public record ToolResult(
    String toolCallId,
    String toolName,
    Object data,
    boolean success,
    String errorMessage
) {
    public static ToolResult ok(String toolCallId, String toolName, Object data) {
        return new ToolResult(toolCallId, toolName, data, true, null);
    }

    public static ToolResult fail(String toolCallId, String toolName, String error) {
        return new ToolResult(toolCallId, toolName, null, false, error);
    }
}
