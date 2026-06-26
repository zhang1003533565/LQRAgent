package com.lqragent.backend.orchestrator.consultation;

import java.util.List;

/** Pipeline / WS 桥接：协商过程事件 */
public interface ConsultationListener {

    void onStart(ConsultationScene scene, List<String> participants, int maxRounds);

    void onRound(int round, String agentId, String role, String summary);

    /** Phase 2 收口：可选 textDelta（stream_transcript 时携带） */
    default void onRound(int round, String agentId, String role, String summary, String textDelta) {
        onRound(round, agentId, role, summary);
    }

    void onEnd(StopReason reason, long durationMs);
}
