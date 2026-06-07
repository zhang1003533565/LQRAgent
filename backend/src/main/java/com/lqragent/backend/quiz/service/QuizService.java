package com.lqragent.backend.quiz.service;

import com.lqragent.backend.agents.learn.stateassessment.service.EffectAssessmentService;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
