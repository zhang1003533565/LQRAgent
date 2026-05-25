package com.lqragent.backend.quiz.service;

import com.lqragent.backend.effectassessment.service.EffectAssessmentService;
import com.lqragent.backend.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.quiz.dto.QuizResultDto;
import com.lqragent.backend.quiz.dto.QuizSubmitRequest;
import com.lqragent.backend.quiz.entity.QuizRecord;
import com.lqragent.backend.quiz.entity.StudyBehavior;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.lqragent.backend.quiz.repository.StudyBehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuizRecordRepository quizRecordRepo;
    private final StudyBehaviorRepository behaviorRepo;
    private final LearnerProfileService profileService;
    private final EffectAssessmentService effectAssessmentService;

    /**
     * 判分规则：
     * - 有 expectedAnswer 时精确匹配（选择题）
     * - 无 expectedAnswer 时 >0 字符即给分（简答占位）
     */
    @Transactional
    public QuizResultDto submit(Long userId, QuizSubmitRequest request) {
        // 判分
        boolean correct = false;
        int score = 0;

        if (request.getExpectedAnswer() != null && !request.getExpectedAnswer().isBlank()) {
            // 选择题精确匹配
            correct = request.getExpectedAnswer().trim().equalsIgnoreCase(request.getAnswer().trim());
            score = correct ? 100 : 0;
        } else {
            // 简答占位：提交即给 60
            correct = true;
            score = 60;
        }

        // 落库
        QuizRecord record = QuizRecord.builder()
                .userId(userId)
                .kpId(request.getKpId())
                .resourceId(request.getResourceId())
                .score(score)
                .isCorrect(correct)
                .answer(request.getAnswer())
                .build();
        record = quizRecordRepo.save(record);
        log.info("[Quiz] 提交答案: userId={}, kpId={}, correct={}, score={}",
                userId, request.getKpId(), correct, score);

        // 记录行为
        StudyBehavior behavior = StudyBehavior.builder()
                .userId(userId)
                .kpId(request.getKpId())
                .action("QUIZ")
                .extra("{\"score\":" + score + ",\"correct\":" + correct + "}")
                .build();
        behaviorRepo.save(behavior);

        // 触发画像更新
        profileService.updateAfterQuiz(userId, correct, score, request.getKpId());

        // 效果评估：低分→动态调整路径
        effectAssessmentService.evaluateQuizResult(userId, request.getKpId(), score, correct);

        return QuizResultDto.builder()
                .id(record.getId())
                .correct(correct)
                .score(score)
                .kpId(request.getKpId())
                .answer(request.getAnswer())
                .build();
    }
}
