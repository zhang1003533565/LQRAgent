package com.lqragent.backend.quiz.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileDetailDto;
import com.lqragent.backend.agents.learnerprofile.service.LearnerProfileService;
import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.agents.path.service.LearningPathService;
import com.lqragent.backend.quiz.dto.QuestionBankListItemDto;
import com.lqragent.backend.quiz.dto.QuestionBankPageDto;
import com.lqragent.backend.quiz.dto.RecommendedPracticeDto;
import com.lqragent.backend.quiz.entity.QuizRecord;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizRecommendationService {

    private final QuizRecordRepository quizRecordRepository;
    private final LearnerProfileService profileService;
    private final LearningPathService learningPathService;
    private final QuizService quizService;
    private final QuizSessionService quizSessionService;

    @Transactional(readOnly = true)
    public List<RecommendedPracticeDto> getRecommendations(Long userId, int limit, String kpId) {
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        List<RecommendedPracticeDto> items = new ArrayList<>();

        for (JsonNode session : quizSessionService.listSessions(userId)) {
            if (!"in_progress".equals(text(session, "status"))) {
                continue;
            }
            String sessionId = text(session, "id");
            items.add(RecommendedPracticeDto.builder()
                    .id("rec-continue-" + sessionId)
                    .title(text(session, "title") != null ? text(session, "title") : "继续上次练习")
                    .reason("继续上次练习")
                    .reasonType("review")
                    .description("从第 " + (session.path("currentIndex").asInt(0) + 1) + " 题继续")
                    .questionCount(session.path("totalQuestions").asInt(0))
                    .difficulty("mixed")
                    .estimatedMinutes(10)
                    .priority(1)
                    .knowledgePointIds(text(session, "kpId") != null ? List.of(text(session, "kpId")) : null)
                    .learningPathNodeId(text(session, "kpId"))
                    .startPayload(RecommendedPracticeDto.StartPayloadDto.builder()
                            .mode("section")
                            .sessionId(sessionId)
                            .build())
                    .build());
        }

        List<QuizRecord> records = quizRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<QuizRecord> wrongRecords = records.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsCorrect()))
                .toList();
        if (!wrongRecords.isEmpty()) {
            Set<String> weakKps = wrongRecords.stream()
                    .map(QuizRecord::getKpId)
                    .filter(k -> k != null && !k.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            String focus = weakKps.stream().findFirst().orElse("薄弱知识点");
            items.add(RecommendedPracticeDto.builder()
                    .id("rec-wrong")
                    .title("错题回顾练习")
                    .reason("薄弱知识点")
                    .reasonType("wrong_questions")
                    .description("针对 " + focus + " 等薄弱点巩固")
                    .questionCount(Math.min(10, wrongRecords.size()))
                    .difficulty("mixed")
                    .estimatedMinutes(15)
                    .priority(2)
                    .knowledgePointIds(new ArrayList<>(weakKps))
                    .startPayload(RecommendedPracticeDto.StartPayloadDto.builder()
                            .mode("wrong_questions")
                            .build())
                    .build());
        }

        String targetKp = resolveTargetKp(userId, kpId);
        if (targetKp != null) {
            QuestionBankPageDto page = quizService.listQuestions(1, 10, null, targetKp);
            if (!page.getItems().isEmpty()) {
                String nodeTitle = resolveKpTitle(userId, targetKp);
                List<Long> questionIds = page.getItems().stream()
                        .map(QuestionBankListItemDto::getId)
                        .toList();
                items.add(RecommendedPracticeDto.builder()
                        .id("rec-path-" + targetKp)
                        .title(nodeTitle + " 专项练习")
                        .reason("当前路径推荐")
                        .reasonType("learning_path")
                        .description("针对当前学习节点 " + nodeTitle + " 巩固练习")
                        .questionCount(questionIds.size())
                        .difficulty("mixed")
                        .estimatedMinutes(Math.max(10, questionIds.size() * 2))
                        .priority(3)
                        .knowledgePointIds(List.of(targetKp))
                        .learningPathNodeId(targetKp)
                        .startPayload(RecommendedPracticeDto.StartPayloadDto.builder()
                                .mode("review")
                                .questionIds(questionIds)
                                .build())
                        .build());
            }
        }

        ProfileDetailDto detail = profileService.getDetail(userId);
        if (detail.getWeakTopics() != null) {
            for (String weak : detail.getWeakTopics()) {
                if (items.size() >= safeLimit) {
                    break;
                }
                if (items.stream().anyMatch(i -> weak.equals(i.getTitle()) || weak.equals(i.getDescription()))) {
                    continue;
                }
                QuestionBankPageDto page = quizService.listQuestions(1, 5, null, weak);
                if (page.getItems().isEmpty()) {
                    continue;
                }
                items.add(RecommendedPracticeDto.builder()
                        .id("rec-weak-" + weak.hashCode())
                        .title(weak + " 强化练习")
                        .reason("画像薄弱点")
                        .reasonType("weak_point")
                        .description("根据学习画像推荐的薄弱知识点练习")
                        .questionCount(page.getItems().size())
                        .difficulty("medium")
                        .estimatedMinutes(12)
                        .priority(4)
                        .knowledgePointIds(List.of(weak))
                        .startPayload(RecommendedPracticeDto.StartPayloadDto.builder()
                                .mode("review")
                                .questionIds(page.getItems().stream().map(QuestionBankListItemDto::getId).toList())
                                .build())
                        .build());
            }
        }

        return items.stream()
                .sorted(Comparator.comparingInt(i -> i.getPriority() != null ? i.getPriority() : 99))
                .limit(safeLimit)
                .toList();
    }

    private String resolveTargetKp(Long userId, String kpIdOverride) {
        if (kpIdOverride != null && !kpIdOverride.isBlank()) {
            return kpIdOverride.trim();
        }
        Optional<LearningPathDto> pathOpt = learningPathService.getCurrentPath(userId);
        if (pathOpt.isEmpty() || pathOpt.get().getNodes() == null) {
            return null;
        }
        return pathOpt.get().getNodes().stream()
                .filter(n -> "ACTIVE".equals(n.getStatus()) && !n.isCompleted())
                .map(LearningPathDto.PathNode::getKpId)
                .findFirst()
                .orElseGet(() -> pathOpt.get().getNodes().stream()
                        .filter(n -> !n.isCompleted())
                        .map(LearningPathDto.PathNode::getKpId)
                        .findFirst()
                        .orElse(null));
    }

    private String resolveKpTitle(Long userId, String kpId) {
        return learningPathService.getCurrentPath(userId)
                .flatMap(path -> path.getNodes().stream()
                        .filter(n -> kpId.equals(n.getKpId()))
                        .map(LearningPathDto.PathNode::getTitle)
                        .findFirst())
                .orElse(kpId);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
