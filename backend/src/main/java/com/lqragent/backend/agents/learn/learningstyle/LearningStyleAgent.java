package com.lqragent.backend.agents.learn.learningstyle;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.learn.learningstyle.tools.DetectLearningStyleTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LearningStyleAgent extends BaseAgent {
    
    private final DetectLearningStyleTool tool;
    
    public LearningStyleAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   DetectLearningStyleTool tool) {
        super("learning_style_agent", llmClient, toolRegistry);
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
