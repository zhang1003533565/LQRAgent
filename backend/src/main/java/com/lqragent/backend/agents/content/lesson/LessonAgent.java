package com.lqragent.backend.agents.content.lesson;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.content.lesson.tools.GenerateLessonTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LessonAgent extends BaseAgent {
    
    private final GenerateLessonTool tool;
    
    public LessonAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                       GenerateLessonTool tool) {
        super("lesson_agent", llmClient, toolRegistry);
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
