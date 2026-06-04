package com.lqragent.backend.agents.resource;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.resource.tools.GenerateLessonTool;
import com.lqragent.backend.agents.resource.tools.GenerateQuizTool;
import com.lqragent.backend.orchestrator.AgentIds;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResourceAgent extends BaseAgent {
    
    private final GenerateLessonTool generateLessonTool;
    private final GenerateQuizTool generateQuizTool;
    
    public ResourceAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                         GenerateLessonTool generateLessonTool,
                         GenerateQuizTool generateQuizTool) {
        super(AgentIds.RESOURCE, llmClient, toolRegistry);
        this.generateLessonTool = generateLessonTool;
        this.generateQuizTool = generateQuizTool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents/resource/prompts/system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(generateLessonTool, generateQuizTool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        String kpIds = request.context() != null
                ? request.context().getOrDefault("kpIds", "").toString()
                : "";
        return String.format("请为以下知识点生成学习资源：\n%s\n\n每个知识点生成讲义和练习题。", kpIds);
    }
}
