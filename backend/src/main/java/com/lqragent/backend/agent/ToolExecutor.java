package com.lqragent.backend.agent;

/**
 * 工具执行器 — 接收 JSON 参数字符串，返回执行结果。
 */
@FunctionalInterface
public interface ToolExecutor {
    Object execute(String argumentsJson);
}
