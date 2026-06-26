package com.lqragent.backend.orchestrator.consultation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * 出题难度评审（Phase 3 quiz_design）：轻量启发式 + 可扩展 LLM。
 */
@Slf4j
@Service
public class QuizReviewService {

    @SuppressWarnings("unchecked")
    public PathReviewDecision review(String profileSummary, Map<String, Object> quizDraft, String goal) {
        if (quizDraft == null || quizDraft.isEmpty()) {
            return PathReviewDecision.revise("题组为空", "请生成至少 3 道题");
        }

        Object questionsObj = quizDraft.get("questions");
        int count = 0;
        if (questionsObj instanceof List<?> list) {
            count = list.size();
        }

        String difficulty = String.valueOf(quizDraft.getOrDefault("difficulty", "中等"));
        String profile = profileSummary != null ? profileSummary.toLowerCase() : "";
        boolean beginner = profile.contains("零基础") || profile.contains("初学") || profile.contains("入门");
        boolean hardQuiz = difficulty.contains("难") || difficulty.contains("hard");

        if (count < 3) {
            return PathReviewDecision.revise(
                    "题目数量偏少（" + count + " 道）",
                    "请增加至 5 道题，覆盖核心知识点");
        }
        if (beginner && hardQuiz) {
            return PathReviewDecision.revise(
                    "难度与初学者画像不匹配",
                    "请将难度调整为「简单」或「中等」，并增加基础概念题");
        }
        if (goal != null && goal.toLowerCase().contains("进阶") && difficulty.contains("简单")) {
            return PathReviewDecision.revise(
                    "难度偏低",
                    "用户目标偏进阶，请提高难度并加入综合应用题");
        }

        return PathReviewDecision.approve("题量与难度匹配（" + count + " 题，" + difficulty + "）");
    }
}
