package com.lqragent.backend.orchestrator.consultation;

import java.util.List;

public record ConsultationRoundRecord(
        int round,
        String agentId,
        String role,
        String summary
) {
    public static ConsultationRoundRecord of(int round, String agentId, String role, String summary) {
        return new ConsultationRoundRecord(round, agentId, role, summary);
    }
}
