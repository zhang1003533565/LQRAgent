package com.lqragent.backend.agents.base;

import java.util.Map;

/**
 * Agent 工具接口
 * 每个工具代表 Agent 可以执行的一个动作
 */
public interface AgentTool {
    
    /**
     * 工具名称（英文，下划线分隔）
     */
    String name();
    
    /**
     * 工具描述（中文，给 LLM 看的）
     */
    String description();
    
    /**
     * 工具参数的 JSON Schema
     * 用于告诉 LLM 这个工具接受哪些参数
     */
    Map<String, Object> parameterSchema();
    
    /**
     * 执行工具
     * 
     * @param args 工具参数
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> args);
    
    /**
     * 工具执行结果
     */
    record ToolResult(
        boolean success,
        String content,
        Map<String, Object> metadata
    ) {
        public static ToolResult success(String content) {
            return new ToolResult(true, content, Map.of());
        }
        
        public static ToolResult success(String content, Map<String, Object> metadata) {
            return new ToolResult(true, content, metadata);
        }
        
        public static ToolResult failure(String error) {
            return new ToolResult(false, error, Map.of());
        }
    }
}
