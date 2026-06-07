package com.lqragent.backend.quiz.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.quiz.dto.NextQuestionDto;
import com.lqragent.backend.quiz.dto.QuestionBankDetailDto;
import com.lqragent.backend.quiz.dto.QuestionBankPageDto;
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

@Tag(name = "答题", description = "学生答题与题库接口")
@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizService quizService;
    private final CurrentUserService currentUserService;
    private final QuizRecordRepository quizRecordRepository;

    @Operation(summary = "获取题目列表", description = "分页获取启用中的题目列表，可按题型和知识点筛选")
    @GetMapping("/questions")
    public ApiResponse<QuestionBankPageDto> listQuestions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String knowledgePoint) {
        return ApiResponse.ok(quizService.listQuestions(page, size, questionType, knowledgePoint));
    }

    @Operation(summary = "获取题目详情", description = "根据题目ID获取单题详情")
    @GetMapping("/questions/{id}")
    public ApiResponse<QuestionBankDetailDto> getQuestionDetail(@PathVariable Long id) {
        return ApiResponse.ok(quizService.getQuestionDetail(id));
    }

    @Operation(summary = "提交用户答案", description = "根据题目ID判分并记录答题结果")
    @PostMapping("/submit")
    public ApiResponse<QuizResultDto> submit(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody QuizSubmitRequest request) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizService.submit(userId, request));
    }

    @Operation(summary = "获取下一道题", description = "根据当前题目ID返回下一道启用题目的ID")
    @GetMapping("/questions/{id}/next")
    public ApiResponse<NextQuestionDto> getNextQuestion(@PathVariable Long id) {
        return ApiResponse.ok(quizService.getNextQuestion(id));
    }

    @Operation(summary = "我的答题记录", description = "按时间倒序返回当前用户的答题历史")
    @GetMapping("/records")
    public ApiResponse<List<QuizRecord>> getRecords(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }

    @Operation(summary = "答题统计", description = "返回当前用户的答题正确率")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> getStats(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        long total = quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId).size();
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
