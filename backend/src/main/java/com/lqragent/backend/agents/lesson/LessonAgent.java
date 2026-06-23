package com.lqragent.backend.agents.lesson;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.lesson.tools.GenerateLessonTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class LessonAgent extends BaseAgent {

    private final GenerateLessonTool tool;

    public LessonAgent(RedisStreamsService streams, LlmClient llmClient,
                        AgentToolRegistry toolRegistry, GenerateLessonTool tool,
                        PromptService promptService) {
        super(AgentIds.LESSON, streams, llmClient, toolRegistry, promptService);
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
        return String.format("请生成讲义: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.LESSON,
                "讲义生成",
                "为知识点生成结构化讲义、章节讲解与学习材料",
                List.of("lesson", "lecture", "讲义", "课程"),
                List.of(ToolSpec.of("generate_lesson", "生成讲义")),
                List.of("text"),
                List.of("lesson", "text"),
                1, 45000L
        );
    }
}
