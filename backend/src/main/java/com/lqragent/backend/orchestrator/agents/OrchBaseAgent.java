package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.Map;

/**
 * Orchestrator 版本的 BaseAgent
 * 桥接消息队列系统和新的 Agent LLM 推理系统
 */
@Slf4j
public abstract class OrchBaseAgent extends com.lqragent.backend.orchestrator.agents.BaseAgent {
    
    protected final ApplicationContext applicationContext;
    protected final ObjectMapper mapper = new ObjectMapper();
    
    protected OrchBaseAgent(String agentId, RedisStreamsService streams, ApplicationContext applicationContext) {
        super(agentId, streams);
        this.applicationContext = applicationContext;
    }
    
    /**
     * 获取对应的 Agent Bean 名称（子类实现）
     */
    protected abstract String getAgentBeanName();
    
    @Override
    protected AgentMessage process(AgentMessage request) {
        String taskId = request.getTaskId();
        String action = (String) request.getContent().getOrDefault("action", "process");
        
        sendProgress(taskId, agentId + " 智能体开始工作...");
        
        try {
            // 获取新的 Agent 实例
            BaseAgent agent = applicationContext.getBean(getAgentBeanName(), BaseAgent.class);
            
            // 构建 AgentRequest
            BaseAgent.AgentRequest agentRequest = new BaseAgent.AgentRequest(
                    action,
                    (String) request.getContent().getOrDefault("goal", ""),
                    request.getContent()
            );
            
            // 调用 Agent 的 process 方法（LLM 推理循环）
            sendProgress(taskId, "正在进行 LLM 推理...");
            BaseAgent.AgentResponse response = agent.process(agentRequest);
            
            if (response.success()) {
                sendProgress(taskId, agentId + " LLM 推理完成");
                
                // LLM 推理完成后，调用真实 Service 获取结构化数据
                Map<String, Object> serviceResult = callService(request, action);
                
                if (serviceResult != null && !serviceResult.isEmpty()) {
                    sendProgress(taskId, agentId + " 完成");
                    Map<String, Object> finalResult = new java.util.HashMap<>(serviceResult);
                    finalResult.put("llm_analysis", response.content());
                    return AgentMessage.inform(taskId, agentId, AgentIds.ORCHESTRATOR, finalResult);
                } else {
                    // Service 调用失败，返回 LLM 分析结果
                    sendProgress(taskId, agentId + " 完成（仅 LLM 分析）");
                    Map<String, Object> resultData = new java.util.HashMap<>();
                    resultData.put("result", response.content());
                    resultData.put("executions", response.executions());
                    return AgentMessage.inform(taskId, agentId, AgentIds.ORCHESTRATOR, resultData);
                }
            } else {
                sendProgress(taskId, agentId + " 失败: " + response.error());
                return AgentMessage.error(taskId, agentId, response.error());
            }
            
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            log.error("[{}] process failed: {}", agentId, errorMsg, e);
            sendProgress(taskId, agentId + " 异常: " + errorMsg);
            return AgentMessage.error(taskId, agentId, errorMsg);
        }
    }
    
    /**
     * 调用真实 Service 获取结构化数据（子类可覆盖）
     * 默认返回 null，表示不调用 Service
     */
    protected Map<String, Object> callService(AgentMessage request, String action) {
        return null;
    }
}
