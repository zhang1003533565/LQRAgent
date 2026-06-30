package com.lqragent.backend.agents.diagram;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.diagram.tools.GenerateDiagramTool;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class DiagramAgent extends BaseAgent {

    private final GenerateDiagramTool tool;

    public DiagramAgent(RedisStreamsService streams, LlmClient llmClient,
                        AgentToolRegistry toolRegistry, GenerateDiagramTool tool,
                        PromptService promptService) {
        super("diagram_agent", streams, llmClient, toolRegistry, promptService);
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
        return String.format("请执行 diagram 相关任务: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                "diagram_agent",
                "图表生成",
                "生成流程图、思维导图、UML 等代码图表（mermaid）",
                List.of("diagram", "mindmap", "flowchart", "uml", "mermaid"),
                List.of(ToolSpec.of("generate_diagram", "生成图表")),
                List.of("text"),
                List.of("diagram"),
                1, 60000L
        );
    }
}
