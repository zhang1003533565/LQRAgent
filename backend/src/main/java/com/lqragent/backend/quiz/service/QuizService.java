package com.lqragent.backend.quiz.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lqragent.backend.agents.effectassessment.service.EffectAssessmentService;
import com.lqragent.backend.agents.learn.difficulty.tools.AdjustDifficultyTool;
import com.lqragent.backend.agents.learn.learningstyle.tools.DetectLearningStyleTool;
import com.lqragent.backend.agents.learn.spacedrepetition.tools.GetReviewScheduleTool;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.chat.entity.UserMemory.MemoryType;
import com.lqragent.backend.chat.service.UserMemoryService;
import com.lqragent.backend.common.exception.BusinessException;
import com.lqragent.backend.quiz.dto.NextQuestionDto;
import com.lqragent.backend.quiz.dto.QuestionBankDetailDto;
import com.lqragent.backend.quiz.dto.QuestionBankListItemDto;
import com.lqragent.backend.quiz.dto.QuestionBankPageDto;
import com.lqragent.backend.quiz.dto.QuizResultDto;
import com.lqragent.backend.quiz.dto.QuizSubmitRequest;
import com.lqragent.backend.quiz.entity.QuestionBank;
import com.lqragent.backend.quiz.entity.QuizRecord;
import com.lqragent.backend.quiz.entity.StudyBehavior;
import com.lqragent.backend.quiz.repository.QuestionBankRepository;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.lqragent.backend.quiz.repository.StudyBehaviorRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int ENABLED_STATUS = 1;

    private final QuestionBankRepository questionBankRepository;
    private final QuizRecordRepository quizRecordRepo;
    private final StudyBehaviorRepository behaviorRepo;
    private final LearnerProfileService profileService;
    private final EffectAssessmentService effectAssessmentService;
    private final GetReviewScheduleTool getReviewScheduleTool;
    private final AdjustDifficultyTool adjustDifficultyTool;
    private final DetectLearningStyleTool detectLearningStyleTool;
    private final UserMemoryService userMemoryService;

    @Transactional(readOnly = true)
    public QuestionBankPageDto listQuestions(int page, int size, String questionType, String knowledgePoint) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(safePage - 1, safeSize);

        Page<QuestionBank> result;
        boolean hasQuestionType = questionType != null && !questionType.isBlank();
        boolean hasKnowledgePoint = knowledgePoint != null && !knowledgePoint.isBlank();

        if (hasQuestionType && hasKnowledgePoint) {
            result = questionBankRepository.findByStatusAndQuestionTypeAndKnowledgePoint(
                    ENABLED_STATUS, questionType.trim(), knowledgePoint.trim(), pageable);
        } else if (hasQuestionType) {
            result = questionBankRepository.findByStatusAndQuestionType(
                    ENABLED_STATUS, questionType.trim(), pageable);
        } else if (hasKnowledgePoint) {
            result = questionBankRepository.findByStatusAndKnowledgePoint(
                    ENABLED_STATUS, knowledgePoint.trim(), pageable);
        } else {
            result = questionBankRepository.findByStatus(ENABLED_STATUS, pageable);
        }

        List<QuestionBankListItemDto> items = result.getContent().stream()
                .map(this::toListItemDto)
                .toList();

        return QuestionBankPageDto.builder()
                .items(items)
                .page(safePage)
                .size(safeSize)
                .total(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Transactional(readOnly = true)
    public QuestionBankDetailDto getQuestionDetail(Long questionId) {
        return toDetailDto(requireEnabledQuestion(questionId));
    }

    @Transactional(readOnly = true)
    public NextQuestionDto getNextQuestion(Long currentQuestionId) {
        QuestionBank next = questionBankRepository
                .findFirstByStatusAndIdGreaterThanOrderByIdAsc(ENABLED_STATUS, currentQuestionId)
                .orElse(null);

        return NextQuestionDto.builder()
                .hasNext(next != null)
                .nextQuestionId(next != null ? next.getId() : null)
                .build();
    }

    @Transactional
    public QuizResultDto submit(Long userId, QuizSubmitRequest request) {
        QuestionBank question = requireEnabledQuestion(request.getQuestionId());
        String kpId = resolveKpId(request, question);
        boolean correct = isAnswerCorrect(question.getCorrectAnswer(), request.getAnswer());
        int score = correct ? 100 : 0;

        QuizRecord record = QuizRecord.builder()
                .userId(userId)
                .questionId(question.getId())
                .kpId(kpId)
                .resourceId(request.getResourceId())
                .score(score)
                .isCorrect(correct)
                .answer(request.getAnswer())
                .build();
        record = quizRecordRepo.save(record);

        log.info("[Quiz] submit answer: userId={}, questionId={}, kpId={}, correct={}, score={}",
                userId, question.getId(), kpId, correct, score);

        StudyBehavior behavior = StudyBehavior.builder()
                .userId(userId)
                .kpId(kpId)
                .action("QUIZ")
                .extra("{\"questionId\":" + question.getId() + ",\"score\":" + score + ",\"correct\":" + correct + "}")
                .build();
        behaviorRepo.save(behavior);

        profileService.updateAfterQuiz(userId, correct, score, kpId);
        effectAssessmentService.evaluateQuizResult(userId, kpId, score, correct);

        if (!correct || score < 60) {
            effectAssessmentService.analyzeWeaknessAsync(userId);
        }

        // 异步触发学习科学层分析（不阻塞答题响应）
        triggerLearningScienceAsync(userId);

        return QuizResultDto.builder()
                .id(record.getId())
                .questionId(question.getId())
                .correct(correct)
                .score(score)
                .kpId(kpId)
                .answer(request.getAnswer())
                .correctAnswer(question.getCorrectAnswer())
                .analysis(question.getAnalysis())
                .build();
    }

    /**
     * 异步触发学习科学层分析（间隔复习、难度推荐、学习风格），不阻塞答题响应。
     * 复习计划会写入用户记忆，供后续对话上下文使用。
     */
    private void triggerLearningScienceAsync(Long userId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // 1. 间隔复习计划
            try {
                var reviewResult = getReviewScheduleTool.execute(Map.of("userId", userId));
                if (reviewResult.success()) {
                    log.info("[Quiz] 学习科学层 - 间隔复习计划已生成: userId={}", userId);
                    // 将复习计划保存到用户记忆
                    try {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        var node = mapper.readTree(reviewResult.content());
                        String summary = node.path("summary").asText("");
                        if (!summary.isBlank() && !summary.contains("不需要")) {
                            userMemoryService.addMemory(userId, MemoryType.LEARNING_PROGRESS,
                                    "[自动] 复习提醒: " + summary, "spaced_repetition");
                        }
                    } catch (Exception e) {
                        log.warn("[Quiz] 保存复习计划记忆失败: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.warn("[Quiz] 间隔复习计划失败: {}", e.getMessage());
            }

            // 2. 难度推荐
            try {
                var diffResult = adjustDifficultyTool.execute(Map.of("userId", userId));
                if (diffResult.success()) {
                    log.info("[Quiz] 学习科学层 - 难度推荐已生成: userId={}", userId);
                }
            } catch (Exception e) {
                log.warn("[Quiz] 难度推荐失败: {}", e.getMessage());
            }

            // 3. 学习风格分析
            try {
                var styleResult = detectLearningStyleTool.execute(Map.of("userId", userId));
                if (styleResult.success()) {
                    log.info("[Quiz] 学习科学层 - 学习风格分析完成: userId={}", userId);
                }
            } catch (Exception e) {
                log.warn("[Quiz] 学习风格分析失败: {}", e.getMessage());
            }
        });
    }

    private QuestionBank requireEnabledQuestion(Long questionId) {
        return questionBankRepository.findById(questionId)
                .filter(question -> question.getStatus() != null && question.getStatus() == ENABLED_STATUS)
                .orElseThrow(() -> BusinessException.notFound("题目不存在或已禁用"));
    }

    private String resolveKpId(QuizSubmitRequest request, QuestionBank question) {
        if (request.getKpId() != null && !request.getKpId().isBlank()) {
            return request.getKpId().trim();
        }
        if (question.getKnowledgePoint() != null && !question.getKnowledgePoint().isBlank()) {
            return question.getKnowledgePoint().trim();
        }
        return "unknown";
    }

    private boolean isAnswerCorrect(String correctAnswer, String userAnswer) {
        if (correctAnswer == null || correctAnswer.isBlank()) {
            return false;
        }
        return correctAnswer.trim().equalsIgnoreCase(userAnswer == null ? "" : userAnswer.trim());
    }

    private QuestionBankListItemDto toListItemDto(QuestionBank question) {
        return QuestionBankListItemDto.builder()
                .id(question.getId())
                .title(question.getTitle())
                .questionType(question.getQuestionType())
                .difficulty(question.getDifficulty())
                .knowledgePoint(question.getKnowledgePoint())
                .status(question.getStatus())
                .build();
    }

    private QuestionBankDetailDto toDetailDto(QuestionBank question) {
        return QuestionBankDetailDto.builder()
                .id(question.getId())
                .title(question.getTitle())
                .codeContent(question.getCodeContent())
                .questionType(question.getQuestionType())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .difficulty(question.getDifficulty())
                .knowledgePoint(question.getKnowledgePoint())
                .status(question.getStatus())
                .analysis(question.getAnalysis())
                .build();
    }
}
