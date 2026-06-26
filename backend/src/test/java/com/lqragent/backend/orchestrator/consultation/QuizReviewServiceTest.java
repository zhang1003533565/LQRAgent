package com.lqragent.backend.orchestrator.consultation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class QuizReviewServiceTest {

    private final QuizReviewService service = new QuizReviewService();

    @Test
    void approvesBalancedQuiz() {
        Map<String, Object> quiz = Map.of(
                "topic", "变量",
                "difficulty", "中等",
                "questions", List.of(1, 2, 3, 4, 5));
        PathReviewDecision decision = service.review("有一定基础", quiz, "练习变量");
        assertTrue(decision.approved());
    }

    @Test
    void revisesWhenTooFewQuestions() {
        Map<String, Object> quiz = Map.of(
                "topic", "变量",
                "difficulty", "中等",
                "questions", List.of(1));
        PathReviewDecision decision = service.review("", quiz, "练习变量");
        assertFalse(decision.approved());
    }

    @Test
    void revisesHardQuizForBeginner() {
        Map<String, Object> quiz = new LinkedHashMap<>();
        quiz.put("topic", "装饰器");
        quiz.put("difficulty", "困难");
        quiz.put("questions", List.of(1, 2, 3, 4));
        PathReviewDecision decision = service.review("零基础初学者", quiz, "学 Python");
        assertFalse(decision.approved());
        assertEquals("难度与初学者画像不匹配", decision.summary());
    }
}
