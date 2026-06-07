package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "题目详情")
public class QuestionBankDetailDto {

    @Schema(description = "题目ID")
    private Long id;

    @Schema(description = "题目内容（题干）")
    private String title;

    @Schema(description = "Python代码片段")
    private String codeContent;

    @Schema(description = "题型")
    private String questionType;

    @Schema(description = "选项A")
    private String optionA;

    @Schema(description = "选项B")
    private String optionB;

    @Schema(description = "选项C")
    private String optionC;

    @Schema(description = "选项D")
    private String optionD;

    @Schema(description = "难度")
    private Integer difficulty;

    @Schema(description = "所属知识点")
    private String knowledgePoint;

    @Schema(description = "状态")
    private Integer status;

    @Schema(description = "题目解析")
    private String analysis;
}
