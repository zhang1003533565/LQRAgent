package com.lqragent.backend.agents.learn.path;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.learn.path.tools.GeneratePathTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LearningPathAgent extends BaseAgent {
    
    private final GeneratePathTool generatePathTool;
    
    public LearningPathAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                             GeneratePathTool generatePathTool) {
        super(AgentIds.LEARNING_PATH, llmClient, toolRegistry);
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
        return String.format("请为用户 %d 生成学习路径，目标：%s", userId, request.goal());
    }
}
