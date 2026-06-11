package com.lqragent.backend.agents.serve.qa;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.serve.qa.tools.SearchKnowledgeTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class QaAgent extends BaseAgent {

    private final SearchKnowledgeTool searchKnowledgeTool;

    public QaAgent(RedisStreamsService streams, LlmClient llmClient,
                    AgentToolRegistry toolRegistry, SearchKnowledgeTool searchKnowledgeTool,
                    PromptService promptService) {
        super(AgentIds.QA, streams, llmClient, toolRegistry, promptService);
        this.searchKnowledgeTool = searchKnowledgeTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(searchKnowledgeTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    /**
     * 带对话历史的处理
     */
    public AgentMessage processWithHistory(AgentMessage request, List<Map<String, Object>> history) {
        return executeLlmLoop(request, history);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        StringBuilder sb = new StringBuilder();
        sb.append("学生提问：").append(goal).append("\n\n");
        sb.append("请用中文回答。如果知识库有相关内容就使用，否则基于通用知识回答。");
        sb.append("如果有用户画像信息，请根据用户的薄弱点和学习偏好进行针对性回答。");
        return sb.toString();
    }
}
