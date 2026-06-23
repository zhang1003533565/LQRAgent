package com.lqragent.backend.agents.learningstyle;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.learningstyle.tools.DetectLearningStyleTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class LearningStyleAgent extends BaseAgent {

    private final DetectLearningStyleTool tool;

    public LearningStyleAgent(RedisStreamsService streams, LlmClient llmClient,
                               AgentToolRegistry toolRegistry, DetectLearningStyleTool tool,
                               PromptService promptService) {
        super(AgentIds.LEARNING_STYLE, streams, llmClient, toolRegistry, promptService);
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
        return String.format("请执行 learningstyle 相关任务: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.LEARNING_STYLE,
                "学习风格识别",
                "识别学习者视觉/听觉/动手等学习风格偏好并更新画像",
                List.of("learning_style", "style", "preference", "学习风格"),
                List.of(ToolSpec.of("detect_learning_style", "检测学习风格")),
                List.of("profile", "text"),
                List.of("profile"),
                1, 25000L
        );
    }
}
