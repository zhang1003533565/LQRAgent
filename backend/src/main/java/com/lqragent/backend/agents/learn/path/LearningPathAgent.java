package com.lqragent.backend.agents.learn.path;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.path.tools.GeneratePathTool;
import com.lqragent.backend.orchestrator.AgentIds;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class LearningPathAgent extends BaseAgent {
    
    private final GeneratePathTool generatePathTool;
    
    public LearningPathAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                             GeneratePathTool generatePathTool, AgentRegistry agentRegistry) {
        super(AgentIds.LEARNING_PATH, llmClient, toolRegistry, agentRegistry);
        this.generatePathTool = generatePathTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/learningpath/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(generatePathTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        Long userId = request.context() != null
                ? Long.parseLong(request.context().getOrDefault("userId", "0").toString())
                : 0L;
        
        StringBuilder sb = new StringBuilder();
        sb.append("请为用户 ").append(userId).append(" 生成学习路径，目标：").append(request.goal()).append("\n\n");
        
        // 尝试获取学习效果评估（Agent 间通信）
        if (userId > 0) {
            try {
                AgentResponse effectResp = requestPeer("effect_agent", 
                    Map.of("goal", "评估学习效果", "userId", String.valueOf(userId)));
                if (effectResp.success()) {
                    sb.append("用户当前学习效果：\n").append(effectResp.content()).append("\n\n");
                    sb.append("请根据上述评估结果，优先安排薄弱知识点的学习路径。");
                    log.info("[{}] 获取到学习效果评估，将优化路径", agentId);
                }
            } catch (Exception e) {
                log.warn("[{}] 获取学习效果失败: {}", agentId, e.getMessage());
            }
        }
        
        return sb.toString();
    }
}
