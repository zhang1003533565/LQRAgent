package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "用户题目偏好（收藏与标记）")
public class QuizPreferencesDto {

    private List<Long> favoriteQuestionIds;
    private List<Long> markedQuestionIds;
}
