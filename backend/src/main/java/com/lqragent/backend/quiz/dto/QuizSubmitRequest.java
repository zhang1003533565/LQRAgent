package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "答题提交请求")
@Data
public class QuizSubmitRequest {

    @Schema(description = "题目ID")
    @NotNull
    private Long questionId;

    @Schema(description = "知识点ID，为空时默认取题目所属知识点")
    private String kpId;

    @Schema(description = "关联资源ID")
    private Long resourceId;

    @Schema(description = "学生答案")
    @NotBlank
    private String answer;
}
