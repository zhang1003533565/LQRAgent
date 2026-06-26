package com.lqragent.backend.orchestrator.consultation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 2：Profile / Path / Difficulty 协商引擎。
 * consultation.enabled=false 时 fallback 为单次 generatePath。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationEngine {

    public static final String LISTENER_KEY = "consultation.listener";

    private static final List<String> PATH_PARTICIPANTS = List.of(
            AgentIds.PROFILE, AgentIds.LEARNING_PATH, AgentIds.DIFFICULTY);

    private final AppRuntimeConfig runtimeConfig;
    private final LearningPathService learningPathService;
    private final LearnerProfileService learnerProfileService;
    private final ConsultationAgentInvoker consultationAgentInvoker;
    private final ConsultationLogService consultationLogService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentResponse consultAsAgentResponse(AgentRequest request, TaskContext context) {
        long start = System.currentTimeMillis();
        ConsultationListener listener = resolveListener(context);
        ConsultationTranscript transcript = new ConsultationTranscript(
                ConsultationScene.PATH_GENERATION, PATH_PARTICIPANTS);

        try {
            Long userId = parseUserId(request, context);
            String goal = request.goal() != null ? request.goal() : "";
            String profileSummary = resolveProfileSummary(request, context, userId);

            if (!runtimeConfig.isConsultationEnabled()
                    || !runtimeConfig.isConsultationSceneEnabled("path_generation")) {
                log.info("[Consultation] disabled, fallback generatePath");
                LearningPathDto path = learningPathService.generatePath(userId, goal, null);
                writeContext(context, transcript, StopReason.FALLBACK, start, path);
                return toAgentResponse(path, "单步生成路径");
            }

            int maxRounds = runtimeConfig.getConsultationMaxRounds();
            long deadline = start + runtimeConfig.getConsultationTimeoutMs();
            listener.onStart(ConsultationScene.PATH_GENERATION, PATH_PARTICIPANTS, maxRounds);

            String workingGoal = goal;
            String feedback = null;
            String pendingRevisionFeedback = null;
            LearningPathDto finalPath = null;
            StopReason stopReason = StopReason.MAX_ROUNDS;

            for (int round = 1; round <= maxRounds; round++) {
                if (System.currentTimeMillis() > deadline) {
                    log.warn("[Consultation] timeout after {} ms", runtimeConfig.getConsultationTimeoutMs());
                    stopReason = StopReason.TIMEOUT;
                    break;
                }

                if (round == 1 && profileSummary != null && !profileSummary.isBlank()) {
                    String constraintSummary = summarizeProfileConstraints(profileSummary);
                    emitRound(listener, transcript, round, AgentIds.PROFILE, "constraints", constraintSummary);
                }

                if (feedback != null && !feedback.isBlank()) {
                    workingGoal = goal + "\n调整建议：" + feedback;
                }

                finalPath = learningPathService.generatePath(userId, workingGoal, null);
                String draftSummary = finalPath.getPlanDescription() != null
                        ? finalPath.getPlanDescription()
                        : "已生成 " + (finalPath.getNodes() != null ? finalPath.getNodes().size() : 0) + " 个节点";
                emitRound(listener, transcript, round, AgentIds.LEARNING_PATH, "draft", draftSummary);

                if (context != null) {
                    context.put("path.draft", finalPath);
                    context.put("consultation.round", round);
                }

                PathReviewDecision review = consultationAgentInvoker.reviewPath(
                        goal, context, finalPath, profileSummary);
                String reviewRole = review.approved() ? "approve" : "revise";
                emitRound(listener, transcript, round, AgentIds.DIFFICULTY, reviewRole, review.summary());

                if (review.approved()) {
                    stopReason = StopReason.CONSENSUS;
                    break;
                }
                feedback = review.feedback();
                pendingRevisionFeedback = feedback;

                if (System.currentTimeMillis() > deadline) {
                    log.warn("[Consultation] timeout after round {}", round);
                    stopReason = StopReason.TIMEOUT;
                    break;
                }
            }

            if (finalPath == null) {
                finalPath = learningPathService.generatePath(userId, goal, null);
                if (stopReason != StopReason.TIMEOUT) {
                    stopReason = StopReason.FALLBACK;
                }
            } else if (pendingRevisionFeedback != null && !pendingRevisionFeedback.isBlank()) {
                finalPath = learningPathService.applyConsultationRevision(
                        finalPath, profileSummary, goal, pendingRevisionFeedback);
            }

            writeContext(context, transcript, stopReason, start, finalPath);
            long duration = System.currentTimeMillis() - start;
            listener.onEnd(stopReason, duration);
            return toAgentResponse(finalPath, "协商生成路径");
        } catch (Exception e) {
            log.error("[Consultation] failed: {}", e.getMessage(), e);
            long duration = System.currentTimeMillis() - start;
            listener.onEnd(StopReason.FALLBACK, duration);
            return AgentResponse.failure(e.getMessage());
        }
    }

    private void emitRound(ConsultationListener listener, ConsultationTranscript transcript,
                           int round, String agentId, String role, String summary) {
        String textDelta = runtimeConfig.isConsultationStreamTranscript() ? summary : null;
        listener.onRound(round, agentId, role, summary, textDelta);
        transcript.addRound(ConsultationRoundRecord.of(round, agentId, role, summary));
    }

    private void writeContext(TaskContext context, ConsultationTranscript transcript,
                              StopReason stopReason, long start, LearningPathDto path) {
        if (context == null) {
            return;
        }
        transcript.setStopReason(stopReason);
        transcript.setDurationMs(System.currentTimeMillis() - start);
        context.put("path.final", path);
        context.put("consultation.transcript", transcript);
        Map<String, Object> stepResult = pathToStepMap(path);
        context.setResult("path_consult", stepResult);
        context.setResult("path_gen", stepResult);
        consultationLogService.persistIfEnabled(context, transcript);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pathToStepMap(LearningPathDto path) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("goal", path.getGoal());
        map.put("planDescription", path.getPlanDescription());
        map.put("content", path.getPlanDescription());
        if (path.getNodes() != null) {
            map.put("nodes", objectMapper.convertValue(path.getNodes(), List.class));
            map.put("nodeCount", path.getNodes().size());
        }
        if (path.getPathId() != null) {
            map.put("pathId", path.getPathId());
        }
        return map;
    }

    private AgentResponse toAgentResponse(LearningPathDto path, String content) {
        Map<String, Object> meta = pathToStepMap(path);
        return AgentResponse.success(
                path.getPlanDescription() != null ? path.getPlanDescription() : content,
                List.of(),
                meta);
    }

    private String resolveProfileSummary(AgentRequest request, TaskContext context, Long userId) {
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
        if (request.context() != null) {
            Object profile = request.context().get("profile");
            if (profile instanceof Map<?, ?> m) {
                Object s = m.get("summary");
                if (s != null) {
                    return String.valueOf(s);
                }
            }
        }
        if (userId != null) {
            try {
                return learnerProfileService.getProfileSummary(userId);
            } catch (Exception e) {
                log.debug("[Consultation] profile summary unavailable: {}", e.getMessage());
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
            Object listener = context.get(LISTENER_KEY);
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
