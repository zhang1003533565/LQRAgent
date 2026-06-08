package com.lqragent.backend.agents.learn.learningstyle;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learn.learningstyle.tools.DetectLearningStyleTool;

@Component
public class LearningStyleAgent extends BaseAgent {
    
    private final DetectLearningStyleTool tool;
    
    public LearningStyleAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                       DetectLearningStyleTool tool, AgentRegistry agentRegistry) {
        super("learning_style_agent", llmClient, toolRegistry, agentRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.learningstyle.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 learningstyle 相关任务: %s", request.goal());
    }
}
