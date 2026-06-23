package com.lqragent.backend.agents.recommendation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.recommendation.tools.GetRecommendationTool;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class RecommendationAgent extends BaseAgent {

    private final GetRecommendationTool tool;

    public RecommendationAgent(RedisStreamsService streams, LlmClient llmClient,
                                AgentToolRegistry toolRegistry, GetRecommendationTool tool,
                                PromptService promptService) {
        super("recommendation_agent", streams, llmClient, toolRegistry, promptService);
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
        String action = String.valueOf(request.getContent().getOrDefault("action", "process"));
        if ("recommend".equals(action)) {
            String userId = String.valueOf(request.getContent().getOrDefault("userId", "1"));
            Object pathCtx = request.getContent().get("adjusted_path");
            String context = pathCtx != null ? String.valueOf(pathCtx) : "";
            return informFromToolResult(request.getTaskId(), tool.execute(Map.of(
                    "userId", userId,
                    "context", context
            )));
        }
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        return String.format("请执行 recommendation 相关任务: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                "recommendation_agent",
                "学习推荐",
                "根据学习路径和薄弱点推荐资源、课程、练习",
                List.of("recommend", "recommendation", "suggest"),
                List.of(ToolSpec.of("get_recommendation", "获取推荐")),
                List.of("learning_path", "weakness_profile", "profile"),
                List.of("multi_card", "text"),
                1, 30000L
        );
    }
}
