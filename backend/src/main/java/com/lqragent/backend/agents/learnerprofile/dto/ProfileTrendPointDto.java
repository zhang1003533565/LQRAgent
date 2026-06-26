package com.lqragent.backend.agents.learnerprofile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "学习趋势数据点")
public class ProfileTrendPointDto {

    private String date;
    private Integer overallMasteryRate;
    private Integer accuracyRate;
    private Integer learningDurationMinutes;
    private Integer completedNodeCount;
    private Integer completedQuestionCount;
}
