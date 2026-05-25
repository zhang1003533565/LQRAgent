package com.lqragent.backend.quiz.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.quiz.dto.QuizResultDto;
import com.lqragent.backend.quiz.dto.QuizSubmitRequest;
import com.lqragent.backend.quiz.service.QuizService;
import com.lqragent.backend.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@Tag(name = "答题", description = "学生答题提交与记录")
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "提交答案", description = "判分+落记录+触发画像更新")
    @PostMapping("/submit")
    public ApiResponse<QuizResultDto> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody QuizSubmitRequest request) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizService.submit(userId, request));
    }
}
