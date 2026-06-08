package com.lqragent.backend.agents.check.content;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.check.content.tools.CheckQualityTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.prompt.service.PromptService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class QualityAgent extends BaseAgent {
    
    private final CheckQualityTool checkQualityTool;
    
    public QualityAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                        CheckQualityTool checkQualityTool, AgentRegistry agentRegistry,
                        PromptService promptService) {
        super(AgentIds.QUALITY, llmClient, toolRegistry, agentRegistry, promptService);
        this.checkQualityTool = checkQualityTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(checkQualityTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        String resourceIds = request.context() != null
                ? request.context().getOrDefault("resourceIds", "").toString()
                : "";
        String userId = request.context() != null
                ? request.context().getOrDefault("userId", "").toString()
                : "";
        
        StringBuilder sb = new StringBuilder();
        sb.append("请检查以下资源的质量：\n").append(resourceIds).append("\n\n");
        sb.append("如果发现质量问题，请说明具体问题并提供建议。");
        
        // 如果有 userId，可以调用推荐 Agent
        if (!userId.isBlank()) {
            try {
                AgentResponse recResp = requestPeer("recommendation_agent",
                    Map.of("goal", "获取替代资源推荐", "userId", userId, "context", "质量检查中"));
                if (recResp.success()) {
                    sb.append("\n\n相关推荐资源：\n").append(recResp.content());
                    log.info("[{}] 获取到推荐资源", agentId);
                }
            } catch (Exception e) {
                log.warn("[{}] 获取推荐失败: {}", agentId, e.getMessage());
            }
        }
        
        return sb.toString();
    }
}
