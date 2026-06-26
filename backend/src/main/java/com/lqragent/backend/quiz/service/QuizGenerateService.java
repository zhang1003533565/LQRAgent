package com.lqragent.backend.quiz.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.common.exception.BusinessException;
import com.lqragent.backend.quiz.dto.QuestionBankDetailDto;
import com.lqragent.backend.quiz.dto.QuestionBankListItemDto;
import com.lqragent.backend.quiz.dto.QuestionBankPageDto;
import com.lqragent.backend.quiz.dto.QuizGenerateRequest;
import com.lqragent.backend.quiz.dto.QuizGenerateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuizGenerateService {

    private final QuizService quizService;
    private final QuizSessionService quizSessionService;
    private final QuizPracticeSessionFactory sessionFactory;
    private final LearningPathService learningPathService;

    @Transactional
    public QuizGenerateResponse generate(Long userId, QuizGenerateRequest request) {
        String kpId = request.getKpId().trim();
        int count = request.getCount() != null ? Math.min(Math.max(request.getCount(), 1), 30) : 10;

        QuestionBankPageDto page = quizService.listQuestions(1, count, null, kpId);
        List<QuestionBankListItemDto> items = filterByDifficulty(page.getItems(), request.getDifficulty());
        if (items.isEmpty()) {
            throw BusinessException.of("该知识点暂无练习题，可先在聊天中请 AI 生成题目");
        }

        List<Long> questionIds = new ArrayList<>();
        List<QuestionBankDetailDto> details = new ArrayList<>();
        for (QuestionBankListItemDto item : items) {
            questionIds.add(item.getId());
            details.add(quizService.getQuestionDetail(item.getId()));
        }

        String title = request.getTitle();
        if (title == null || title.isBlank()) {
            title = resolveKpTitle(userId, kpId) + " 专项练习";
        }

        String sessionId = "session-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);
        ObjectNode session = sessionFactory.buildSession(sessionId, title, "ai_generated", kpId, details);
        quizSessionService.saveSession(userId, session);

        return QuizGenerateResponse.builder()
                .sessionId(sessionId)
                .questionIds(questionIds)
                .title(title)
                .build();
    }

    private List<QuestionBankListItemDto> filterByDifficulty(
            List<QuestionBankListItemDto> items,
            String difficulty) {
        if (difficulty == null || difficulty.isBlank() || "mixed".equalsIgnoreCase(difficulty)) {
            return items;
        }
        return items.stream()
                .filter(item -> matchesDifficulty(item.getDifficulty(), difficulty))
                .toList();
    }

    private boolean matchesDifficulty(Integer value, String difficulty) {
        String level = mapDifficulty(value);
        return level.equalsIgnoreCase(difficulty.trim());
    }

    private String mapDifficulty(Integer value) {
        if (value == null || value <= 1) {
            return "easy";
        }
        if (value == 2) {
            return "medium";
        }
        return "hard";
    }

    private String resolveKpTitle(Long userId, String kpId) {
        return learningPathService.getCurrentPath(userId)
                .flatMap(path -> path.getNodes().stream()
                        .filter(n -> kpId.equals(n.getKpId()))
                        .map(LearningPathDto.PathNode::getTitle)
                        .findFirst())
                .orElse(kpId);
    }
}
