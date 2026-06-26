package com.lqragent.backend.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "按知识点生成练习响应")
public class QuizGenerateResponse {

    private String sessionId;
    private List<Long> questionIds;
    private String title;
}
