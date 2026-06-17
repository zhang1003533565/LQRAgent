package com.lqragent.backend.agents.assessment;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.assessment.tools.GradeAnswerTool;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class AssessmentAgent extends BaseAgent {

    private final GradeAnswerTool tool;

    public AssessmentAgent(RedisStreamsService streams, LlmClient llmClient,
                            AgentToolRegistry toolRegistry, GradeAnswerTool tool,
                            PromptService promptService) {
        super("assessment_agent", streams, llmClient, toolRegistry, promptService);
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
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        return String.format("请执行 assessment 相关任务: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                "assessment_agent",
                "学习评估",
                "批改答题并分析薄弱点",
                List.of("assessment", "grade", "analyze", "evaluate", "score"),
                List.of(ToolSpec.of("grade_answer", "批改答案")),
                List.of("quiz"),
                List.of("assessment", "weakness_profile"),
                1, 60000L
        );
    }
}
