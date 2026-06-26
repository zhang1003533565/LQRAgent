package com.lqragent.backend.orchestrator.consultation;

import java.util.ArrayList;
import java.util.List;

public class ConsultationTranscript {

    private final ConsultationScene scene;
    private final List<String> participants;
    private final List<ConsultationRoundRecord> rounds = new ArrayList<>();
    private StopReason stopReason;
    private long durationMs;

    public ConsultationTranscript(ConsultationScene scene, List<String> participants) {
        this.scene = scene;
        this.participants = participants != null ? List.copyOf(participants) : List.of();
    }

    public void addRound(ConsultationRoundRecord round) {
        rounds.add(round);
    }

    public ConsultationScene scene() {
        return scene;
    }

    public List<String> participants() {
        return participants;
    }

    public List<ConsultationRoundRecord> rounds() {
        return List.copyOf(rounds);
    }

    public StopReason stopReason() {
        return stopReason;
    }

    public void setStopReason(StopReason stopReason) {
        this.stopReason = stopReason;
    }

    public long durationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
