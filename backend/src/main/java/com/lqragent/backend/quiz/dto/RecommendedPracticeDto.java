package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "推荐练习项")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedPracticeDto {

    private String id;
    private String title;
    private String reason;
    private String reasonType;
    private String description;
    private int questionCount;
    private String difficulty;
    private Integer estimatedMinutes;
    private Integer priority;
    private List<String> knowledgePointIds;
    private String learningPathNodeId;
    private StartPayloadDto startPayload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StartPayloadDto {
        private String mode;
        private String sectionId;
        private List<Long> questionIds;
        private String sessionId;
    }
}
