package com.lqragent.backend.agents.difficulty;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.difficulty.tools.AdjustDifficultyTool;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.consultation.PathReviewDecision;
import com.lqragent.backend.orchestrator.consultation.PathReviewService;
import com.lqragent.backend.orchestrator.consultation.QuizReviewService;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DifficultyAgent extends BaseAgent {

    private final AdjustDifficultyTool tool;
    private final PathReviewService pathReviewService;
    private final QuizReviewService quizReviewService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DifficultyAgent(RedisStreamsService streams, LlmClient llmClient,
                            AgentToolRegistry toolRegistry, AdjustDifficultyTool tool,
                            PathReviewService pathReviewService,
                            QuizReviewService quizReviewService,
                            PromptService promptService) {
        super(AgentIds.DIFFICULTY, streams, llmClient, toolRegistry, promptService);
        this.tool = tool;
        this.pathReviewService = pathReviewService;
        this.quizReviewService = quizReviewService;
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
    public AgentResponse process(AgentRequest request, TaskContext context) {
        if ("review_path".equals(request.action())) {
            return reviewPath(request, context);
        }
        if ("review_quiz".equals(request.action())) {
            return reviewQuiz(request, context);
        }
        return super.process(request, context);
    }

    @SuppressWarnings("unchecked")
    private AgentResponse reviewQuiz(AgentRequest request, TaskContext context) {
        Map<String, Object> draft = resolveQuizDraft(request, context);
        if (draft == null || draft.isEmpty()) {
            return AgentResponse.failure("缺少题目草案");
        }
        String goal = request.goal() != null ? request.goal() : "";
        String profileSummary = resolveProfileSummary(request, context);
        PathReviewDecision decision = quizReviewService.review(profileSummary, draft, goal);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("approved", decision.approved());
        meta.put("summary", decision.summary());
        if (decision.feedback() != null) {
            meta.put("feedback", decision.feedback());
        }
        meta.put("role", decision.approved() ? "approve" : "revise");
        return AgentResponse.success(decision.summary(), List.of(), meta);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveQuizDraft(AgentRequest request, TaskContext context) {
        if (context != null) {
            Map<String, Object> fromTask = asStringObjectMap(context.get("quiz.draft"));
            if (fromTask != null) {
                return fromTask;
            }
        }
        if (request.context() != null) {
            return asStringObjectMap(request.context().get("quiz"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringObjectMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    private AgentResponse reviewPath(AgentRequest request, TaskContext context) {
        LearningPathDto path = resolvePathDraft(request, context);
        if (path == null) {
            return AgentResponse.failure("缺少路径草案");
        }
        String goal = request.goal() != null ? request.goal() : "";
        String profileSummary = resolveProfileSummary(request, context);
        PathReviewDecision decision = pathReviewService.review(profileSummary, path, goal);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("approved", decision.approved());
        meta.put("summary", decision.summary());
        if (decision.feedback() != null) {
            meta.put("feedback", decision.feedback());
        }
        meta.put("role", decision.approved() ? "approve" : "revise");
        return AgentResponse.success(decision.summary(), List.of(), meta);
    }

    @SuppressWarnings("unchecked")
    private LearningPathDto resolvePathDraft(AgentRequest request, TaskContext context) {
        if (context != null) {
            Object draft = context.get("path.draft");
            if (draft instanceof LearningPathDto dto) {
                return dto;
            }
        }
        if (request.context() != null && request.context().get("path") instanceof Map<?, ?> pathMap) {
            return objectMapper.convertValue(pathMap, LearningPathDto.class);
        }
        return null;
    }

    private String resolveProfileSummary(AgentRequest request, TaskContext context) {
        if (context != null) {
            Map<String, Object> profileStep = context.getResult("profile");
            if (profileStep != null) {
                Object content = profileStep.get("content");
                if (content != null && !String.valueOf(content).isBlank()) {
                    return String.valueOf(content);
                }
                Object summary = profileStep.get("summary");
                if (summary != null) {
                    return String.valueOf(summary);
                }
            }
        }
        if (request.context() != null && request.context().get("profileSummary") != null) {
            return String.valueOf(request.context().get("profileSummary"));
        }
        return "";
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = (String) request.getContent().getOrDefault("goal", "");
        return String.format("请执行 difficulty 相关任务: %s", goal);
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.DIFFICULTY,
                "难度调节",
                "根据学习者水平调整内容难度、题目难度与学习节奏",
                List.of("difficulty", "level", "难度", "adjust", "review_path", "review_quiz"),
                List.of(
                        ToolSpec.of("adjust_difficulty", "调整难度"),
                        ToolSpec.of("review_path", "评审学习路径难度"),
                        ToolSpec.of("review_quiz", "评审题目难度")),
                List.of("profile", "text", "learning_path"),
                List.of("profile"),
                1, 20000L
        );
    }
}
