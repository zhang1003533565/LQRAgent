package com.lqragent.backend.orchestrator.planning;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 规划 Clarify 会话状态（Phase 1 内存实现，避免改表）
 */
@Component
public class PlanningSessionStateStore {

    private final ConcurrentHashMap<Long, PlanningSessionState> states = new ConcurrentHashMap<>();

    public PlanningSessionState get(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        PlanningSessionState state = states.get(sessionId);
        if (state != null && state.isExpired()) {
            states.remove(sessionId);
            return null;
        }
        return state;
    }

    public void saveAwaitingClarify(Long sessionId, String originalGoal, List<String> questions) {
        if (sessionId == null || originalGoal == null || originalGoal.isBlank()) {
            return;
        }
        states.put(sessionId, PlanningSessionState.awaitingClarify(originalGoal, questions));
    }

    public void saveAwaitingResourceConfirm(Long sessionId, String originalGoal) {
        if (sessionId == null || originalGoal == null || originalGoal.isBlank()) {
            return;
        }
        states.put(sessionId, PlanningSessionState.awaitingResourceConfirm(originalGoal));
    }

    public void clear(Long sessionId) {
        if (sessionId != null) {
            states.remove(sessionId);
        }
    }
}
