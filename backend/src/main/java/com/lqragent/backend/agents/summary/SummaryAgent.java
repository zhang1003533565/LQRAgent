package com.lqragent.backend.agents.summary;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.summary.tools.GenerateSummaryTool;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class SummaryAgent extends BaseAgent {

    private final GenerateSummaryTool tool;

    public SummaryAgent(RedisStreamsService streams, LlmClient llmClient,
                        AgentToolRegistry toolRegistry, GenerateSummaryTool tool,
                        PromptService promptService) {
        super("summary_agent", streams, llmClient, toolRegistry, promptService);
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
        return String.format("请执行 summarygen 相关任务: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                "summary_agent",
                "内容摘要",
                "对长文本/对话/学习内容生成摘要",
                List.of("summary", "summarize", "abstract"),
                List.of(ToolSpec.of("generate_summary", "生成摘要")),
                List.of("text"),
                List.of("summary", "text"),
                1, 30000L
        );
    }
}
