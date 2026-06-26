package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "收藏/取消收藏题目")
public class QuizToggleFavoriteRequest {

    @NotNull
    @Schema(description = "true=收藏 false=取消收藏")
    private Boolean favorite;
}
