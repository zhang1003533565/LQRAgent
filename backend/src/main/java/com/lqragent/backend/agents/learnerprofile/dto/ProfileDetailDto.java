package com.lqragent.backend.agents.learnerprofile.dto;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "学生画像详情（6 维度 + 知识点掌握地图）")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileDetailDto {

    private ProfileSummaryDto summary;

    // === 展平的 summary 字段（前端直接访问，无需 .summary.xxx）===
    @Schema(description = "知识水平：BEGINNER/INTERMEDIATE/ADVANCED")
    private String knowledgeLevel;
    @Schema(description = "当前学习目标")
    private String learningGoal;
    @Schema(description = "认知风格：visual/reading/practice")
    private String cognitiveStyle;
    @Schema(description = "学习节奏：SLOW/NORMAL/FAST")
    private String learningPace;

    // === 计算字段 ===
    @Schema(description = "已完成（MASTERED）知识点数量")
    private int completedKpCount;
    @Schema(description = "连续学习天数")
    private int streakDays;

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
