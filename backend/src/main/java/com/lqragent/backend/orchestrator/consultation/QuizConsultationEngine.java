package com.lqragent.backend.orchestrator.consultation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.AgentTool.ToolResult;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.quiz.tools.GenerateQuizTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 3：Quiz / Difficulty / Profile 出题协商引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuizConsultationEngine {

    private static final List<String> QUIZ_PARTICIPANTS = List.of(
            AgentIds.PROFILE, AgentIds.QUIZ, AgentIds.DIFFICULTY);

    private final AppRuntimeConfig runtimeConfig;
    private final GenerateQuizTool generateQuizTool;
    private final LearnerProfileService learnerProfileService;
    private final ConsultationAgentInvoker consultationAgentInvoker;
    private final ConsultationLogService consultationLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentResponse consultAsAgentResponse(AgentRequest request, TaskContext context) {
        long start = System.currentTimeMillis();
        ConsultationListener listener = resolveListener(context);
        ConsultationTranscript transcript = new ConsultationTranscript(
                ConsultationScene.QUIZ_DESIGN, QUIZ_PARTICIPANTS);

        try {
            Long userId = parseUserId(request, context);
            String goal = request.goal() != null ? request.goal() : "";
            String profileSummary = resolveProfileSummary(request, context, userId);
            String topic = resolveTopic(request, goal);
            String difficulty = resolveDifficulty(request, profileSummary);
            int count = resolveCount(request);

            if (!runtimeConfig.isConsultationEnabled()
                    || !runtimeConfig.isConsultationSceneEnabled("quiz_design")) {
                log.info("[QuizConsultation] disabled, fallback generateQuiz");
                Map<String, Object> quiz = generateQuizDraft(topic, difficulty, count, null);
                writeContext(context, transcript, StopReason.FALLBACK, start, quiz);
                return toAgentResponse(quiz, "单步生成题目");
            }

            int maxRounds = runtimeConfig.getConsultationMaxRounds();
            long deadline = start + runtimeConfig.getConsultationTimeoutMs();
            listener.onStart(ConsultationScene.QUIZ_DESIGN, QUIZ_PARTICIPANTS, maxRounds);

            String feedback = null;
            Map<String, Object> finalQuiz = null;
            StopReason stopReason = StopReason.MAX_ROUNDS;

            for (int round = 1; round <= maxRounds; round++) {
                if (System.currentTimeMillis() > deadline) {
                    stopReason = StopReason.TIMEOUT;
                    break;
                }

                if (round == 1 && profileSummary != null && !profileSummary.isBlank()) {
                    emitRound(listener, transcript, round, AgentIds.PROFILE, "constraints",
                            summarizeProfileConstraints(profileSummary));
                }

                String extra = feedback;
                if (feedback != null && !feedback.isBlank()) {
                    difficulty = adjustDifficulty(difficulty, feedback);
                    count = Math.max(3, count);
                }

                finalQuiz = generateQuizDraft(topic, difficulty, count, extra);
                String draftSummary = summarizeQuizDraft(finalQuiz);
                emitRound(listener, transcript, round, AgentIds.QUIZ, "draft", draftSummary);

                if (context != null) {
                    context.put("quiz.draft", finalQuiz);
                    context.put("consultation.round", round);
                }

                PathReviewDecision review = consultationAgentInvoker.reviewQuiz(
                        goal, context, finalQuiz, profileSummary);
                String reviewRole = review.approved() ? "approve" : "revise";
                emitRound(listener, transcript, round, AgentIds.DIFFICULTY, reviewRole, review.summary());

                if (review.approved()) {
                    stopReason = StopReason.CONSENSUS;
                    break;
                }
                feedback = review.feedback();

                if (System.currentTimeMillis() > deadline) {
                    stopReason = StopReason.TIMEOUT;
                    break;
                }
            }

            if (finalQuiz == null) {
                finalQuiz = generateQuizDraft(topic, difficulty, count, null);
                if (stopReason != StopReason.TIMEOUT) {
                    stopReason = StopReason.FALLBACK;
                }
            }

            writeContext(context, transcript, stopReason, start, finalQuiz);
            listener.onEnd(stopReason, System.currentTimeMillis() - start);
            return toAgentResponse(finalQuiz, "协商生成题目");
        } catch (Exception e) {
            log.error("[QuizConsultation] failed: {}", e.getMessage(), e);
            listener.onEnd(StopReason.FALLBACK, System.currentTimeMillis() - start);
            return AgentResponse.failure(e.getMessage());
        }
    }

    private Map<String, Object> generateQuizDraft(String topic, String difficulty, int count, String extraContext) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", topic);
        args.put("difficulty", difficulty);
        args.put("count", count);
        if (extraContext != null && !extraContext.isBlank()) {
            args.put("context", extraContext);
        }
        ToolResult result = generateQuizTool.execute(args);
        if (!result.success() || result.content() == null || result.content().isBlank()) {
            return Map.of(
                    "title", topic + "练习题",
                    "topic", topic,
                    "difficulty", difficulty,
                    "questions", List.of());
        }
        try {
            com.lqragent.backend.agents.base.AgentResponse response =
                    objectMapper.readValue(result.content(), com.lqragent.backend.agents.base.AgentResponse.class);
            if (response.getData() != null) {
                Map<String, Object> parsed = objectMapper.convertValue(
                        response.getData(), new TypeReference<Map<String, Object>>() {});
                if (parsed != null && !parsed.isEmpty()) {
                    return parsed;
                }
            }
        } catch (Exception e) {
            log.debug("[QuizConsultation] parse tool response failed: {}", e.getMessage());
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    result.content(), new TypeReference<Map<String, Object>>() {});
            if (parsed != null && !parsed.isEmpty()) {
                return parsed;
            }
        } catch (Exception ignored) {
        }
        return Map.of(
                "title", topic + "练习题",
                "topic", topic,
                "difficulty", difficulty,
                "raw", result.content(),
                "questions", List.of());
    }

    private void writeContext(TaskContext context, ConsultationTranscript transcript,
                              StopReason stopReason, long start, Map<String, Object> quiz) {
        if (context == null) {
            return;
        }
        transcript.setStopReason(stopReason);
        transcript.setDurationMs(System.currentTimeMillis() - start);
        context.put("quiz.final", quiz);
        context.put("consultation.transcript", transcript);
        Map<String, Object> stepResult = quizToStepMap(quiz);
        context.setResult("quiz_consult", stepResult);
        context.setResult("quiz", stepResult);
        consultationLogService.persistIfEnabled(context, transcript);
    }

    private Map<String, Object> quizToStepMap(Map<String, Object> quiz) {
        Map<String, Object> map = new LinkedHashMap<>(quiz);
        map.put("success", true);
        map.putIfAbsent("artifactKind", "quiz");
        Object questions = quiz.get("questions");
        if (questions instanceof List<?> list) {
            map.put("questionCount", list.size());
        }
        map.put("content", String.valueOf(quiz.getOrDefault("title", "练习题")));
        return map;
    }

    private AgentResponse toAgentResponse(Map<String, Object> quiz, String content) {
        return AgentResponse.success(
                String.valueOf(quiz.getOrDefault("title", content)),
                List.of(),
                quizToStepMap(quiz));
    }

    @SuppressWarnings("unchecked")
    private String summarizeQuizDraft(Map<String, Object> quiz) {
        if (quiz == null) {
            return "未生成题目";
        }
        Object questions = quiz.get("questions");
        int count = questions instanceof List<?> list ? list.size() : 0;
        String difficulty = String.valueOf(quiz.getOrDefault("difficulty", "中等"));
        String topic = String.valueOf(quiz.getOrDefault("topic", "综合"));
        return "「" + topic + "」" + count + " 题 · 难度 " + difficulty;
    }

    private static String adjustDifficulty(String current, String feedback) {
        String f = feedback.toLowerCase();
        if (f.contains("简单") || f.contains("基础") || f.contains("降低")) {
            return "简单";
        }
        if (f.contains("难") || f.contains("进阶") || f.contains("提高")) {
            return "困难";
        }
        return current != null ? current : "中等";
    }

    private String resolveTopic(AgentRequest request, String goal) {
        if (request.context() != null && request.context().get("topic") != null) {
            return String.valueOf(request.context().get("topic"));
        }
        return goal != null && !goal.isBlank() ? goal : "Python基础";
    }

    private String resolveDifficulty(AgentRequest request, String profileSummary) {
        if (request.context() != null && request.context().get("difficulty") != null) {
            return String.valueOf(request.context().get("difficulty"));
        }
        String profile = profileSummary != null ? profileSummary.toLowerCase() : "";
        if (profile.contains("进阶") || profile.contains("高级")) {
            return "困难";
        }
        if (profile.contains("零基础") || profile.contains("初学")) {
            return "简单";
        }
        return "中等";
    }

    private int resolveCount(AgentRequest request) {
        if (request.context() != null && request.context().get("count") != null) {
            try {
                return Integer.parseInt(String.valueOf(request.context().get("count")));
            } catch (NumberFormatException ignored) {
            }
        }
        return 5;
    }

    private void emitRound(ConsultationListener listener, ConsultationTranscript transcript,
                           int round, String agentId, String role, String summary) {
        String textDelta = runtimeConfig.isConsultationStreamTranscript() ? summary : null;
        listener.onRound(round, agentId, role, summary, textDelta);
        transcript.addRound(ConsultationRoundRecord.of(round, agentId, role, summary));
    }

    private String resolveProfileSummary(AgentRequest request, TaskContext context, Long userId) {
        if (context != null) {
            Map<String, Object> profileStep = context.getResult("profile");
            if (profileStep != null) {
                Object content = profileStep.get("content");
                if (content != null && !String.valueOf(content).isBlank()) {
                    return String.valueOf(content);
                }
            }
        }
        if (userId != null) {
            try {
                return learnerProfileService.getProfileSummary(userId);
            } catch (Exception e) {
                log.debug("[QuizConsultation] profile unavailable: {}", e.getMessage());
            }
        }
        return "";
    }

    private static String summarizeProfileConstraints(String profileSummary) {
        if (profileSummary == null || profileSummary.isBlank()) {
            return "暂无画像约束";
        }
        String trimmed = profileSummary.trim().replace('\n', ' ');
        return trimmed.length() > 120 ? trimmed.substring(0, 120) + "…" : trimmed;
    }

    private Long parseUserId(AgentRequest request, TaskContext context) {
        if (context != null && context.getUserId() != null) {
            try {
                return Long.parseLong(context.getUserId());
            } catch (NumberFormatException ignored) {
            }
        }
        if (request.context() != null && request.context().get("userId") != null) {
            try {
                return Long.parseLong(String.valueOf(request.context().get("userId")));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1L;
    }

    private ConsultationListener resolveListener(TaskContext context) {
        if (context != null) {
            Object listener = context.get(ConsultationEngine.LISTENER_KEY);
            if (listener instanceof ConsultationListener cl) {
                return cl;
            }
        }
        return new ConsultationListener() {
            @Override
            public void onStart(ConsultationScene scene, List<String> participants, int maxRounds) {
            }

            @Override
            public void onRound(int round, String agentId, String role, String summary) {
            }

            @Override
            public void onEnd(StopReason reason, long durationMs) {
            }
        };
    }
}
