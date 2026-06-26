package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "标记/取消标记题目")
public class QuizToggleMarkRequest {

    @NotNull
    @Schema(description = "true=标记 false=取消标记")
    private Boolean marked;
}
