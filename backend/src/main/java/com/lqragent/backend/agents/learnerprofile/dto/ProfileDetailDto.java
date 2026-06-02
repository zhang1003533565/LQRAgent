package com.lqragent.backend.agents.learner_profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Schema(description = "学生画像详情（6 维度 + 知识点掌握地图）")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDetailDto {

    private ProfileSummaryDto summary;
    private List<String> weakTopics;
    private List<String> recentGoals;
    private List<KnowledgeMapItem> knowledgeMap;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KnowledgeMapItem {
        private String kpId;
        private String title;
        private int mastery;
        private String status;
    }

    public static int masteryPercent(String status) {
        if ("MASTERED".equals(status)) return 100;
        if ("PENDING".equals(status)) return 20;
        return 0;
    }

    public static List<KnowledgeMapItem> fromTopicMastery(Map<String, String> topics) {
        return topics.entrySet().stream()
                .map(e -> KnowledgeMapItem.builder()
                        .kpId(e.getKey())
                        .title(e.getKey())
                        .status(e.getValue())
                        .mastery(masteryPercent(e.getValue()))
                        .build())
                .toList();
    }
}
