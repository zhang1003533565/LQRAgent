package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "答题结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResultDto {

    @Schema(description = "答题记录ID")
    private Long id;

    @Schema(description = "是否正确")
    private Boolean correct;

    @Schema(description = "得分 0-100")
    private Integer score;

    @Schema(description = "知识点ID")
    private String kpId;

    @Schema(description = "学生答案")
    private String answer;
}
