package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "按知识点生成练习请求")
public class QuizGenerateRequest {

    @NotBlank(message = "kpId 不能为空")
    @Schema(description = "知识点 ID")
    private String kpId;

    @Schema(description = "题目数量，默认 10")
    private Integer count;

    @Schema(description = "难度筛选：easy | medium | hard")
    private String difficulty;

    @Schema(description = "练习标题")
    private String title;
}
