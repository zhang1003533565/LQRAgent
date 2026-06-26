package com.lqragent.backend.orchestrator.consultation;

public record PathReviewDecision(
        boolean approved,
        String summary,
        String feedback
) {
    public static PathReviewDecision approve(String summary) {
        return new PathReviewDecision(true, summary, null);
    }

    public static PathReviewDecision revise(String summary, String feedback) {
        return new PathReviewDecision(false, summary, feedback);
    }
}
