package com.lqragent.backend.agents.path;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.path.tools.GeneratePathTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class LearningPathAgent extends BaseAgent {

    private final GeneratePathTool generatePathTool;

    public LearningPathAgent(RedisStreamsService streams, LlmClient llmClient,
                              AgentToolRegistry toolRegistry, GeneratePathTool generatePathTool,
                              PromptService promptService) {
        super(AgentIds.LEARNING_PATH, streams, llmClient, toolRegistry, promptService);
        this.generatePathTool = generatePathTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(generatePathTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String userId = (String) request.getContent().getOrDefault("userId", "0");
        String goal = (String) request.getContent().getOrDefault("goal", "");
        StringBuilder sb = new StringBuilder();
        sb.append("请为用户 ").append(userId).append(" 生成学习路径，目标：").append(goal).append("\n\n");
        return sb.toString();
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.LEARNING_PATH,
                "学习路径规划",
                "生成或调整个性化学习路径",
                List.of("learning_path", "path", "plan", "roadmap", "study_plan"),
                List.of(ToolSpec.of("generate_path", "生成学习路径")),
                List.of("text", "weakness_profile"),
                List.of("learning_path"),
                1, 60000L
        );
    }
}
