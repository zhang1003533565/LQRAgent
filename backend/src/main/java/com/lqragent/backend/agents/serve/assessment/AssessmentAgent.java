package com.lqragent.backend.agents.serve.assessment;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.BaseAgent;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.serve.assessment.tools.GradeAnswerTool;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class AssessmentAgent extends BaseAgent {
    
    private final GradeAnswerTool tool;
    
    public AssessmentAgent(LlmClient llmClient, AgentToolRegistry toolRegistry,
                                   GradeAnswerTool tool, AgentRegistry agentRegistry,
                                   PromptService promptService) {
        super("assessment_agent", llmClient, toolRegistry, agentRegistry, promptService);
        this.tool = tool;
    }
    
    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
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
