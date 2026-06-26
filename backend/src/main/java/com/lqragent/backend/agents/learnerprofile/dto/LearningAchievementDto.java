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
@Schema(description = "学习成就")
public class LearningAchievementDto {

    private String id;
    private String title;
    private String description;
    private boolean achieved;
    private Integer progress;
    private Integer target;
    private String level;
    private String achievedAt;
}
