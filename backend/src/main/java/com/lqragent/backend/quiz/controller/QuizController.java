package com.lqragent.backend.quiz.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.quiz.dto.NextQuestionDto;
import com.lqragent.backend.quiz.dto.QuestionBankDetailDto;
import com.lqragent.backend.quiz.dto.QuestionBankPageDto;
import com.lqragent.backend.quiz.dto.QuizResultDto;
import com.lqragent.backend.quiz.dto.QuizSubmitRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.lqragent.backend.quiz.dto.QuizPreferencesDto;
import com.lqragent.backend.quiz.dto.QuizToggleFavoriteRequest;
import com.lqragent.backend.quiz.dto.QuizToggleMarkRequest;
import com.lqragent.backend.quiz.dto.QuizGenerateRequest;
import com.lqragent.backend.quiz.dto.QuizGenerateResponse;
import com.lqragent.backend.quiz.dto.RecommendedPracticeDto;
import com.lqragent.backend.quiz.entity.QuizRecord;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.lqragent.backend.quiz.service.QuizService;
import com.lqragent.backend.quiz.service.QuizSessionService;
import com.lqragent.backend.quiz.service.QuizGenerateService;
import com.lqragent.backend.quiz.service.QuizRecommendationService;
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
    private final QuizSessionService quizSessionService;
    private final QuizRecommendationService quizRecommendationService;
    private final QuizGenerateService quizGenerateService;
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

    @Operation(summary = "保存练习会话", description = "创建或更新练习会话快照（JSON 与前端 PracticeSession 一致）")
    @PostMapping("/sessions")
    public ApiResponse<JsonNode> saveSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody JsonNode sessionPayload) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizSessionService.saveSession(userId, sessionPayload));
    }

    @Operation(summary = "获取练习会话", description = "按会话 ID 返回完整练习快照")
    @GetMapping("/sessions/{id}")
    public ApiResponse<JsonNode> getSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizSessionService.getSession(userId, id));
    }

    @Operation(summary = "我的练习会话列表", description = "按更新时间倒序返回当前用户的练习会话")
    @GetMapping("/sessions")
    public ApiResponse<List<JsonNode>> listSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizSessionService.listSessions(userId));
    }

    @Operation(summary = "删除练习会话")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> deleteSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable String id) {
        Long userId = currentUserService.requireUserId(userDetails);
        quizSessionService.deleteSession(userId, id);
        return ApiResponse.ok(Map.of("message", "删除成功", "sessionId", id));
    }

    @Operation(summary = "题目偏好", description = "返回收藏与标记的题目 ID 列表")
    @GetMapping("/preferences")
    public ApiResponse<QuizPreferencesDto> getPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizSessionService.getPreferences(userId));
    }

    @Operation(summary = "收藏题目")
    @PostMapping("/questions/{id}/favorite")
    public ApiResponse<Map<String, Object>> favoriteQuestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody QuizToggleFavoriteRequest request) {
        Long userId = currentUserService.requireUserId(userDetails);
        quizSessionService.setFavorite(userId, id, Boolean.TRUE.equals(request.getFavorite()));
        return ApiResponse.ok(Map.of("questionId", id, "favorite", request.getFavorite()));
    }

    @Operation(summary = "标记题目")
    @PostMapping("/questions/{id}/mark")
    public ApiResponse<Map<String, Object>> markQuestion(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody QuizToggleMarkRequest request) {
        Long userId = currentUserService.requireUserId(userDetails);
        quizSessionService.setMarked(userId, id, Boolean.TRUE.equals(request.getMarked()));
        return ApiResponse.ok(Map.of("questionId", id, "marked", request.getMarked()));
    }

    @Operation(summary = "推荐练习", description = "基于错题、路径节点与画像薄弱点推荐练习")
    @GetMapping("/recommendations")
    public ApiResponse<List<RecommendedPracticeDto>> getRecommendations(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) String kpId) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizRecommendationService.getRecommendations(userId, limit, kpId));
    }

    @Operation(summary = "按知识点生成练习", description = "从题库筛选题目并创建练习会话")
    @PostMapping("/generate")
    public ApiResponse<QuizGenerateResponse> generate(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody QuizGenerateRequest request) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(quizGenerateService.generate(userId, request));
    }
}
