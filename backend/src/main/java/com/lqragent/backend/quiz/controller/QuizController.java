package com.lqragent.backend.quiz.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.quiz.dto.QuizResultDto;
import com.lqragent.backend.quiz.dto.QuizSubmitRequest;
import com.lqragent.backend.quiz.entity.QuizRecord;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.lqragent.backend.quiz.service.QuizService;
import com.lqragent.backend.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "答题", description = "学生答题提交与记录")
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final CurrentUserService currentUserService;
    private final QuizRecordRepository quizRecordRepository;

    @Operation(summary = "提交答案", description = "判分+落记录+触发画像更新")
    @PostMapping("/submit")
    public ApiResponse<QuizResultDto> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody QuizSubmitRequest request) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizService.submit(userId, request));
    }

    @Operation(summary = "我的答题记录", description = "按时间倒序返回当前用户的答题历史")
    @GetMapping("/records")
    public ApiResponse<List<QuizRecord>> getRecords(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Operation(summary = "答题统计", description = "返回当前用户的答题正确率和各知识点掌握情况")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        long total = quizRecordRepository.count();
        long correct = quizRecordRepository.countByUserIdAndIsCorrect(userId, true);
        long wrong = quizRecordRepository.countByUserIdAndIsCorrect(userId, false);
        return ApiResponse.ok(Map.of(
                "total", total,
                "correct", correct,
                "wrong", wrong,
                "accuracy", total > 0 ? Math.round(correct * 100.0 / total) : 0
        ));
    }
}
