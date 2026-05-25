package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "答题提交请求")
@Data
public class QuizSubmitRequest {

    @Schema(description = "知识点ID")
    @NotBlank
    private String kpId;

    @Schema(description = "关联资源ID（题目ID）")
    private Long resourceId;

    @Schema(description = "学生答案")
    @NotNull
    private String answer;

    @Schema(description = "参考正确答案（选择题用精确匹配，简答题用关键词匹配）")
    private String expectedAnswer;
}
