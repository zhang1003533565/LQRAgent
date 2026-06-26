package com.lqragent.backend.agents.quiz;

import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.aiserver.tools.AiServerToolFactory;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.orchestrator.consultation.QuizConsultationEngine;
import com.lqragent.backend.agents.base.RagSearchTool;
import com.lqragent.backend.agents.quiz.tools.GenerateQuizTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class QuizAgent extends BaseAgent {

    private final RagSearchTool ragSearchTool;
    private final GenerateQuizTool generateQuizTool;
    private final AiServerToolFactory aiServerToolFactory;
    private final QuizConsultationEngine quizConsultationEngine;

    public QuizAgent(RedisStreamsService streams, LlmClient llmClient,
                     AgentToolRegistry toolRegistry, RagSearchTool ragSearchTool,
                     GenerateQuizTool generateQuizTool, PromptService promptService,
                     AiServerToolFactory aiServerToolFactory,
                     QuizConsultationEngine quizConsultationEngine) {
        super(AgentIds.QUIZ, streams, llmClient, toolRegistry, promptService);
        this.ragSearchTool = ragSearchTool;
        this.generateQuizTool = generateQuizTool;
        this.aiServerToolFactory = aiServerToolFactory;
        this.quizConsultationEngine = quizConsultationEngine;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(ragSearchTool, generateQuizTool, aiServerToolFactory.deepQuestionTool());
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    public AgentResponse process(AgentRequest request, TaskContext context) {
        if ("consult_quiz".equals(request.action())) {
            return quizConsultationEngine.consultAsAgentResponse(request, context);
        }
        return super.process(request, context);
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

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.QUIZ,
                "题目生成",
                "按要求或基于知识库资料生成混合题型练习题",
                List.of("quiz", "exercise", "test", "question", "practice", "deep_question"),
                List.of(
                        ToolSpec.of("search_knowledge", "知识库检索"),
                        ToolSpec.of("generate_quiz", "生成题目"),
                        ToolSpec.of("deep_question", "深度出题")
                ),
                List.of("text", "rag_sources"),
                List.of("quiz"),
                1, 60000L
        );
    }
}
