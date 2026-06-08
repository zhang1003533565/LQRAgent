package com.lqragent.backend.agents.base;

import java.util.List;
import java.util.Map;

import com.lqragent.backend.orchestrator.context.TaskContext;

/**
 * 统一 Agent 接口
 * 所有业务 Agent 都实现此接口，支持 Agent 间通信
 */
public interface AgentInterface {
    
    /**
     * 获取 Agent 唯一标识
     */
    String getAgentId();
    
    /**
     * 处理请求（无上下文）
     */
    BaseAgent.AgentResponse process(BaseAgent.AgentRequest request);
    
    /**
     * 处理请求（带对话历史）
     */
    BaseAgent.AgentResponse process(BaseAgent.AgentRequest request, List<Map<String, Object>> history);
    
    /**
     * 处理请求（带 TaskContext，支持 Agent 间通信）
     * Agent 内部可以通过 TaskContext 共享数据，或通过 requestPeer() 调用其他 Agent
     */
    BaseAgent.AgentResponse process(BaseAgent.AgentRequest request, TaskContext context);
}
