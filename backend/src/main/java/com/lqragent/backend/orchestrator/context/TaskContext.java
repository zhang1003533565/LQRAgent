package com.lqragent.backend.orchestrator.context;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Data;

/**
 * 共享任务上下文
 * 在 Pipeline 执行期间，Agent 间共享数据和中间结果
 */
@Data
public class TaskContext {
    
    /** 任务唯一标识 */
    private final String taskId;
    
    /** 用户ID */
    private final String userId;
    
    /** 会话ID */
    private final String sessionId;
    
    /** 学习目标/用户请求 */
    private final String goal;
    
    /** 创建时间 */
    private final Instant createdAt;
    
    /** 共享数据（Agent 间传递） */
    private final Map<String, Object> sharedData = new ConcurrentHashMap<>();
    
    /** Agent 间传递的中间结果 */
    private final Map<String, Map<String, Object>> agentResults = new ConcurrentHashMap<>();
    
    /** 链路追踪 */
    private final List<TraceSpan> traceSpans = new CopyOnWriteArrayList<>();
    
    public TaskContext(String taskId, String userId, String sessionId, String goal) {
        this.taskId = taskId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.goal = goal;
        this.createdAt = Instant.now();
    }
    
    /**
     * 设置 Agent 结果
     */
    public void setResult(String agentId, Map<String, Object> result) {
        agentResults.put(agentId, result);
    }
    
    /**
     * 获取 Agent 结果
     */
    public Map<String, Object> getResult(String agentId) {
        return agentResults.get(agentId);
    }
    
    /**
     * 添加追踪跨度
     */
    public void addTraceSpan(TraceSpan span) {
        traceSpans.add(span);
    }
    
    /**
     * 设置共享数据
     */
    public void put(String key, Object value) {
        sharedData.put(key, value);
    }
    
    /**
     * 获取共享数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) sharedData.get(key);
    }
    
    /**
     * 获取共享数据（带默认值）
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(String key, T defaultValue) {
        return (T) sharedData.getOrDefault(key, defaultValue);
    }
}
