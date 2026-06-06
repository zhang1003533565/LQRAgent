package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "下一题信息")
public class NextQuestionDto {

    @Schema(description = "是否存在下一题")
    private Boolean hasNext;

    @Schema(description = "下一题ID")
    private Long nextQuestionId;
}
