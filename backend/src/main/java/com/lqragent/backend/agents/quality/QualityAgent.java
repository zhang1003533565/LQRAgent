package com.lqragent.backend.agents.quality;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.quality.tools.CheckQualityTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class QualityAgent extends BaseAgent {

    private final CheckQualityTool checkQualityTool;

    public QualityAgent(RedisStreamsService streams, LlmClient llmClient,
                         AgentToolRegistry toolRegistry, CheckQualityTool checkQualityTool,
                         PromptService promptService) {
        super(AgentIds.QUALITY, streams, llmClient, toolRegistry, promptService);
        this.checkQualityTool = checkQualityTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(checkQualityTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        StringBuilder sb = new StringBuilder();

        // 阶段四新增：支持产物级别的检查请求
        if (request.getContent().containsKey("artifact")) {
            Object artifactObj = request.getContent().get("artifact");
            sb.append("请检查以下 Agent 产物的质量：\n");
            sb.append("产物：").append(artifactObj).append("\n\n");
            sb.append("检查要点：\n");
            sb.append("- 内容完整性：必填字段是否齐全\n");
            sb.append("- 正确性：知识点是否准确\n");
            sb.append("- 符合度：是否符合用户请求\n");
            sb.append("- 格式：是否能被前端正确渲染\n");
            sb.append("如果质量合格，回复'通过'；如果有问题，说明具体问题。");
            return sb.toString();
        }

        // 兼容旧调用
        String resourceIds = (String) request.getContent().getOrDefault("resourceIds", "");
        sb.append("请检查以下资源的质量：\n").append(resourceIds).append("\n\n");
        sb.append("如果发现质量问题，请说明具体问题并提供建议。");
        return sb.toString();
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.QUALITY,
                "质量检查",
                "检查 Agent 产物的完整性、正确性、符合度",
                List.of("quality", "check", "validate", "review"),
                List.of(ToolSpec.of("check_quality", "质量检查")),
                List.of("quiz", "media_image", "video", "summary", "learning_path", "text"),
                List.of("assessment"),
                1, 30000L
        );
    }
}
