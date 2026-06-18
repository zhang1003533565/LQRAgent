package com.lqragent.backend.agents.effect;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.effect.tools.AnalyzeWeaknessTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class EffectAgent extends BaseAgent {

    private final AnalyzeWeaknessTool analyzeWeaknessTool;

    public EffectAgent(RedisStreamsService streams, LlmClient llmClient,
                        AgentToolRegistry toolRegistry, AnalyzeWeaknessTool analyzeWeaknessTool,
                        PromptService promptService) {
        super(AgentIds.EFFECT, streams, llmClient, toolRegistry, promptService);
        this.analyzeWeaknessTool = analyzeWeaknessTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(analyzeWeaknessTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String action = (String) request.getContent().getOrDefault("action", "evaluate");

        // 阶段五新增：基于 assessment 结果更新效果
        if ("evaluate".equals(action)) {
            Object weakness = request.getContent().getOrDefault("weakness",
                    request.getContent().getOrDefault("assessment", ""));
            String userId = (String) request.getContent().getOrDefault("userId", "0");
            StringBuilder sb = new StringBuilder();
            sb.append("用户 ").append(userId).append(" 的最新评估结果：\n");
            sb.append(weakness).append("\n\n");
            sb.append("请更新该用户的学习效果档案：\n");
            sb.append("1. 记录本次评估的薄弱点\n");
            sb.append("2. 更新知识掌握度（mastery score）\n");
            sb.append("3. 标记需要强化的知识点\n");
            sb.append("输出 JSON：{\"userId\": \"...\", \"weakness\": [...], \"mastery\": {...}}");
            return sb.toString();
        }

        // 兼容旧调用
        String userId = (String) request.getContent().getOrDefault("userId", "0");
        return String.format("请分析用户 %s 的学习效果和薄弱点。", userId);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.EFFECT,
                "效果评估",
                "评估学习效果、更新知识掌握度、识别薄弱点",
                List.of("effect", "evaluate", "weakness", "mastery"),
                List.of(ToolSpec.of("analyze_weakness", "分析薄弱点")),
                List.of("assessment"),
                List.of("weakness_profile"),
                1, 30000L
        );
    }
}
