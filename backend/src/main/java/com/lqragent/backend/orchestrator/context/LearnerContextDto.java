package com.lqragent.backend.orchestrator.context;

import java.util.Map;

import lombok.Builder;
import lombok.Data;

/**
 * Pipeline / Planning 启动前的学习者上下文摘要
 */
@Data
@Builder
public class LearnerContextDto {

    private Long userId;
    private String profileSummary;
    private String memorySummary;
    private Map<String, String> topicMastery;
    private String promptBlock;

    public boolean isEmpty() {
        return (profileSummary == null || profileSummary.isBlank())
                && (memorySummary == null || memorySummary.isBlank())
                && (topicMastery == null || topicMastery.isEmpty());
    }
}
