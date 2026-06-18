package com.lqragent.backend.quiz.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lqragent.backend.agents.resourcegeneration.entity.ResourceItem;
import com.lqragent.backend.agents.resourcegeneration.repository.ResourceItemRepository;
import com.lqragent.backend.agents.effectassessment.service.EffectAssessmentService;
import com.lqragent.backend.agents.difficulty.tools.AdjustDifficultyTool;
import com.lqragent.backend.agents.learningstyle.tools.DetectLearningStyleTool;
import com.lqragent.backend.agents.spacedrepetition.tools.GetReviewScheduleTool;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.resourcegeneration.service.ResourceGenerationService;
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
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
import com.lqragent.backend.orchestrator.pipeline.PipelineResult;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.shared.knowledgegraph.service.KnowledgeGraphService;

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
    private final ResourceGenerationService resourceGenerationService;
    private final KnowledgeGraphService kgService;
    private final ResourceItemRepository resourceItemRepository;
    private final PipelineEngine pipelineEngine;

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

        // 题库为空时，尝试从 resource_item 表查找已生成的 QUIZ 资源
        if (result.getTotalElements() == 0 && hasKnowledgePoint) {
            var resourceItems = resourceItemRepository.findByKpIdAndResourceType(
                    knowledgePoint.trim(), ResourceItem.TYPE_QUIZ);
            if (!resourceItems.isEmpty()) {
                List<QuestionBankListItemDto> items = resourceItems.stream()
                        .map(this::resourceToListItemDto)
                        .toList();
                return QuestionBankPageDto.builder()
                        .items(items)
                        .page(safePage)
                        .size(safeSize)
                        .total((long) items.size())
                        .totalPages(1)
                        .build();
            }
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
        // 先查 question_bank 表
        try {
            return toDetailDto(requireEnabledQuestion(questionId));
        } catch (BusinessException e) {
            // 题库没有，尝试从 resource_item 表查找
            var resourceItem = resourceItemRepository.findById(questionId).orElse(null);
            if (resourceItem != null && ResourceItem.TYPE_QUIZ.equals(resourceItem.getResourceType())) {
                return resourceToDetailDto(resourceItem);
            }
            throw e;
        }
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
        // 阶段五新增：异步触发多 Agent 学习闭环（批改→薄弱点→路径调整→资源推荐）
        triggerLearningLoopAsync(userId, question, request, correct, score, kpId);

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
     * 阶段五新增：异步触发学习闭环 Pipeline，不阻塞答题响应。
     */
    private void triggerLearningLoopAsync(Long userId, QuestionBank question, QuizSubmitRequest request,
                                          boolean correct, int score, String kpId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                PipelineConfig config = PipelineTemplates.learningLoop();
                TaskContext context = new TaskContext(
                        "quiz-loop-" + System.currentTimeMillis(),
                        String.valueOf(userId),
                        "quiz-submit",
                        "学生提交题目后进行学习闭环"
                );
                Map<String, Object> quizSubmission = new java.util.LinkedHashMap<>();
                quizSubmission.put("userId", userId);
                quizSubmission.put("questionId", question.getId());
                quizSubmission.put("kpId", kpId);
                quizSubmission.put("question", question.getTitle());
                quizSubmission.put("questionType", question.getQuestionType());
                quizSubmission.put("answer", request.getAnswer());
                quizSubmission.put("correctAnswer", question.getCorrectAnswer());
                quizSubmission.put("correct", correct);
                quizSubmission.put("score", score);
                quizSubmission.put("analysis", question.getAnalysis());
                context.setResult("quiz_submission", Map.of("answers", quizSubmission, "quiz", quizSubmission));

                var result = pipelineEngine.execute(config, context);
                if (result.isSuccess()) {
                    persistLearningLoopResult(userId, question.getId(), result);
                    log.info("[Quiz] 学习闭环 Pipeline 完成: userId={}, questionId={}", userId, question.getId());
                } else {
                    log.warn("[Quiz] 学习闭环 Pipeline 失败: userId={}, questionId={}, error={}",
                            userId, question.getId(), result.getErrorMessage());
                }
            } catch (Exception e) {
                log.warn("[Quiz] 学习闭环 Pipeline 异常: {}", e.getMessage());
            }
        });
    }

    /**
     * 阶段五新增：把学习闭环结果沉淀到用户记忆，让后续聊天/路径/推荐能感知。
     */
    private void persistLearningLoopResult(Long userId, Long questionId, PipelineResult result) {
        try {
            StringBuilder summary = new StringBuilder();
            summary.append("[自动] 答题后的学习闭环结果：题目ID=").append(questionId).append("\n");
            if (result.getStepResults() != null) {
                result.getStepResults().forEach(step -> {
                    if (step.isSuccess() && step.getData() != null) {
                        Object content = step.getData().get("content");
                        if (content != null && !String.valueOf(content).isBlank()) {
                            summary.append("- ").append(step.getStepId()).append("：")
                                    .append(truncateMemory(String.valueOf(content), 500))
                                    .append("\n");
                        }
                    }
                });
            }
            if (summary.length() > 0) {
                userMemoryService.addMemory(userId, MemoryType.LEARNING_PROGRESS,
                        truncateMemory(summary.toString(), 1800), "learning_loop", 3);
            }
        } catch (Exception e) {
            log.warn("[Quiz] 保存学习闭环记忆失败: {}", e.getMessage());
        }
    }

    private String truncateMemory(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
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

    /** 从 ResourceItem（LLM 动态生成）转换为列表项 DTO */
    private QuestionBankListItemDto resourceToListItemDto(ResourceItem item) {
        return QuestionBankListItemDto.builder()
                .id(item.getId())
                .title(item.getTitle())
                .questionType("mixed")
                .difficulty(2)
                .knowledgePoint(item.getKpId())
                .status(1)
                .build();
    }

    /** 从 ResourceItem（LLM 动态生成）转换为详情 DTO */
    private QuestionBankDetailDto resourceToDetailDto(ResourceItem item) {
        return QuestionBankDetailDto.builder()
                .id(item.getId())
                .title(item.getTitle())
                .codeContent(null)
                .questionType("mixed")
                .optionA(null)
                .optionB(null)
                .optionC(null)
                .optionD(null)
                .difficulty(2)
                .knowledgePoint(item.getKpId())
                .status(1)
                .analysis(item.getContent())  // 完整 Markdown 内容放在解析字段
                .build();
    }
}
