package com.lqragent.backend.agents.content.lesson;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.content.lesson.tools.GenerateLessonTool;

@Component
public class LessonAgent extends BaseAgent {
    
    private final GenerateLessonTool tool;
    
    public LessonAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                   GenerateLessonTool tool, AgentRegistry agentRegistry) {
        super("lesson_agent", llmClient, toolRegistry, agentRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/lesson/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请生成讲义: %s", request.goal());
    }
}
