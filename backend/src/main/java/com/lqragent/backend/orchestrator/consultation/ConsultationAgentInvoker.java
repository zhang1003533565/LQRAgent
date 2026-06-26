package com.lqragent.backend.orchestrator.consultation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.base.AgentRequest;
import com.lqragent.backend.agents.base.AgentResponse;
import com.lqragent.backend.agents.base.PeerCallContext;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.context.TaskContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 2 收口：通过 AgentRegistry 调用 participant Agent（等价于受控 peer 调用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConsultationAgentInvoker {

    private final AgentRegistry agentRegistry;
    private final PathReviewService pathReviewService;

    public PathReviewDecision reviewPath(
            String goal, TaskContext context, LearningPathDto draft, String profileSummary) {
        if (context != null) {
            context.put("path.draft", draft);
        }

        var agentOpt = agentRegistry.getAgent(AgentIds.DIFFICULTY);
        if (agentOpt.isPresent()) {
            try {
                PeerCallContext peerCtx = new PeerCallContext().enter(AgentIds.DIFFICULTY);
                Map<String, Object> reqCtx = new HashMap<>();
                reqCtx.put("peerCallContext", peerCtx);
                reqCtx.put("profileSummary", profileSummary);

                AgentResponse response = agentOpt.get().process(
                        new AgentRequest("review_path", goal, reqCtx), context);
                if (response.isSuccess() && response.getMetadata() != null) {
                    Object approved = response.getMetadata().get("approved");
                    String summary = String.valueOf(response.getMetadata().getOrDefault("summary", response.getContent()));
                    String feedback = response.getMetadata().get("feedback") != null
                            ? String.valueOf(response.getMetadata().get("feedback")) : null;
                    if (Boolean.TRUE.equals(approved) || "true".equals(String.valueOf(approved))) {
                        return PathReviewDecision.approve(summary);
                    }
                    if (feedback != null && !feedback.isBlank()) {
                        return PathReviewDecision.revise(summary, feedback);
                    }
                    return PathReviewDecision.revise(summary, summary);
                }
                log.warn("[Consultation] difficulty agent review failed: {}", response.getError());
            } catch (Exception e) {
                log.warn("[Consultation] difficulty agent review error: {}", e.getMessage());
            }
        }

        return pathReviewService.review(profileSummary, draft, goal);
    }
}
