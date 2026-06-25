package com.lqragent.backend.orchestrator.planning;

import java.time.Instant;
import java.util.List;

/**
 * Clarify 多轮会话状态（内存存储，TTL 30 分钟）
 */
public record PlanningSessionState(
        String phase,
        String originalGoal,
        List<String> pendingQuestions,
        Instant updatedAt
) {
    public static final String PHASE_AWAITING_CLARIFY = "AWAITING_CLARIFY";
    public static final String PHASE_AWAITING_RESOURCE_CONFIRM = "AWAITING_RESOURCE_CONFIRM";

    public static PlanningSessionState awaitingClarify(String originalGoal, List<String> questions) {
        return new PlanningSessionState(PHASE_AWAITING_CLARIFY, originalGoal, questions, Instant.now());
    }

    public static PlanningSessionState awaitingResourceConfirm(String originalGoal) {
        return new PlanningSessionState(PHASE_AWAITING_RESOURCE_CONFIRM, originalGoal, List.of(), Instant.now());
    }

    public boolean isExpired() {
        return updatedAt != null && updatedAt.isBefore(Instant.now().minusSeconds(30 * 60));
    }
}
