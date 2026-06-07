package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "题目列表项")
public class QuestionBankListItemDto {

    @Schema(description = "题目ID")
    private Long id;

    @Schema(description = "题目内容（题干）")
    private String title;

    @Schema(description = "题型")
    private String questionType;

    @Schema(description = "难度")
    private Integer difficulty;

    @Schema(description = "所属知识点")
    private String knowledgePoint;

    @Schema(description = "状态")
    private Integer status;
}
