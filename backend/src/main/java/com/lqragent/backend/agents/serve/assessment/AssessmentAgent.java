package com.lqragent.backend.agents.serve.assessment;

import com.lqragent.backend.agents.base.*;
import com.lqragent.backend.agents.serve.assessment.tools.GradeAnswerTool;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AssessmentAgent extends BaseAgent {
    
    private final GradeAnswerTool tool;
    
    public AssessmentAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GradeAnswerTool tool) {
        super("assessment_agent", llmClient, toolRegistry);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return loadPrompt("agents.assessment.prompts.system.md");
    }
    
    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }
    
    @Override
    protected String buildUserMessage(AgentRequest request) {
        return String.format("请执行 assessment 相关任务: %s", request.goal());
    }
}
