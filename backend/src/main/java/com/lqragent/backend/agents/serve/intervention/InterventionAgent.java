package com.lqragent.backend.agents.serve.intervention;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.serve.intervention.tools.GetInterventionTool;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class InterventionAgent extends BaseAgent {
    
    private final GetInterventionTool tool;
    
    public InterventionAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GetInterventionTool tool, AgentRegistry agentRegistry) {
        super("intervention_agent", llmClient, toolRegistry, agentRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.intervention.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        String userId = request.context() != null
                ? request.context().getOrDefault("userId", "").toString()
                : "";
        
        StringBuilder sb = new StringBuilder();
        sb.append("请执行干预分析任务：").append(request.goal()).append("\n\n");
        
        // 尝试获取最近问答记录（Agent 间通信）
        if (!userId.isBlank()) {
            try {
                AgentResponse qaResp = requestPeer("qa_agent", 
                    Map.of("goal", "获取用户最近的学习问题", "userId", userId, "limit", "3"));
                if (qaResp.success()) {
                    sb.append("用户最近的学习问题：\n").append(qaResp.content()).append("\n\n");
                    log.info("[{}] 获取到问答记录，将针对性干预", agentId);
                }
            } catch (Exception e) {
                log.warn("[{}] 获取问答记录失败: {}", agentId, e.getMessage());
            }
        }
        
        return sb.toString();
    }
}
