package com.lqragent.backend.agents.serve.qa;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.serve.qa.tools.SearchKnowledgeTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.prompt.service.PromptService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class QaAgent extends BaseAgent {
    
    private final SearchKnowledgeTool searchKnowledgeTool;
    
    public QaAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                   SearchKnowledgeTool searchKnowledgeTool, AgentRegistry agentRegistry,
                   PromptService promptService) {
        super(AgentIds.QA, llmClient, toolRegistry, agentRegistry, promptService);
        this.searchKnowledgeTool = searchKnowledgeTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(searchKnowledgeTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        String userId = request.context() != null
                ? request.context().getOrDefault("userId", "").toString()
                : "";
        
        StringBuilder sb = new StringBuilder();
        sb.append("学生提问：").append(request.goal()).append("\n\n");
        
        // 尝试获取用户画像（Agent 间通信）
        if (!userId.isBlank()) {
            try {
                AgentResponse profileResp = requestPeer("profile_agent", 
                    Map.of("goal", "获取用户画像", "userId", userId));
                if (profileResp.success()) {
                    sb.append("用户画像信息：\n").append(profileResp.content()).append("\n\n");
                    log.info("[{}] 获取到用户画像，将个性化回答", agentId);
                }
            } catch (Exception e) {
                log.warn("[{}] 获取用户画像失败，使用默认回答: {}", agentId, e.getMessage());
            }
        }
        
        sb.append("请用中文回答。如果知识库有相关内容就使用，否则基于通用知识回答。");
        sb.append("如果有用户画像信息，请根据用户的薄弱点和学习偏好进行针对性回答。");
        return sb.toString();
    }
}
