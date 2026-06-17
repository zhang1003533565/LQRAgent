package com.lqragent.backend.agents.quiz;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.base.RagSearchTool;
import com.lqragent.backend.agents.quiz.tools.GenerateQuizTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class QuizAgent extends BaseAgent {

    private final RagSearchTool ragSearchTool;
    private final GenerateQuizTool generateQuizTool;

    public QuizAgent(RedisStreamsService streams, LlmClient llmClient,
                     AgentToolRegistry toolRegistry, RagSearchTool ragSearchTool,
                     GenerateQuizTool generateQuizTool, PromptService promptService) {
        super(AgentIds.QUIZ, streams, llmClient, toolRegistry, promptService);
        this.ragSearchTool = ragSearchTool;
        this.generateQuizTool = generateQuizTool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(ragSearchTool, generateQuizTool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        Object topic = request.getContent().getOrDefault("topic", "");
        Object questionTypes = request.getContent().getOrDefault("questionTypes", "题型混合");
        Object difficulty = request.getContent().getOrDefault("difficulty", "中等");
        Object count = request.getContent().getOrDefault("count", 5);
        Object context = request.getContent().getOrDefault("context", "");

        return "请生成练习题。\n"
                + "用户原始需求：" + goal + "\n"
                + "主题：" + topic + "\n"
                + "题型要求：" + questionTypes + "\n"
                + "难度：" + difficulty + "\n"
                + "数量：" + count + "\n"
                + "补充上下文：" + context + "\n"
                + "如果用户要求基于知识库、资料、文档或课件出题，请先调用 search_knowledge 检索相关内容，再调用 generate_quiz 生成题目。";
    }
}
