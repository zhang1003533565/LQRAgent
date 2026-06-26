package com.lqragent.backend.quiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lqragent.backend.quiz.dto.QuestionBankDetailDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class QuizPracticeSessionFactory {

    private final ObjectMapper objectMapper;

    public ObjectNode buildSession(
            String sessionId,
            String title,
            String mode,
            String kpId,
            List<QuestionBankDetailDto> questions) {
        ObjectNode session = objectMapper.createObjectNode();
        session.put("id", sessionId);
        session.put("title", title);
        session.put("mode", mode);
        session.put("totalQuestions", questions.size());
        session.put("currentIndex", 0);
        session.put("completedCount", 0);
        session.put("correctCount", 0);
        session.put("wrongCount", 0);
        session.put("startedAt", Instant.now().toString());
        session.put("status", "in_progress");
        if (kpId != null && !kpId.isBlank()) {
            session.put("kpId", kpId);
        }

        ArrayNode questionsNode = objectMapper.createArrayNode();
        for (QuestionBankDetailDto detail : questions) {
            questionsNode.add(toQuestionNode(detail));
        }
        session.set("questions", questionsNode);
        return session;
    }

    private ObjectNode toQuestionNode(QuestionBankDetailDto detail) {
        ObjectNode q = objectMapper.createObjectNode();
        q.put("id", String.valueOf(detail.getId()));
        q.put("type", mapQuestionType(detail.getQuestionType()));
        q.put("title", detail.getTitle());
        q.put("content", detail.getTitle());
        q.put("difficulty", mapDifficulty(detail.getDifficulty()));
        q.put("status", "unanswered");

        if (detail.getCodeContent() != null) {
            q.put("codeContent", detail.getCodeContent());
        }
        if (detail.getAnalysis() != null) {
            q.put("analysis", detail.getAnalysis());
        }

        ArrayNode options = buildOptions(detail, q.get("type").asText());
        if (options.size() > 0) {
            q.set("options", options);
        }

        if (detail.getKnowledgePoint() != null && !detail.getKnowledgePoint().isBlank()) {
            ArrayNode kps = objectMapper.createArrayNode();
            ObjectNode kp = objectMapper.createObjectNode();
            kp.put("id", detail.getKnowledgePoint());
            kp.put("name", detail.getKnowledgePoint());
            kps.add(kp);
            q.set("knowledgePoints", kps);
        }

        return q;
    }

    private ArrayNode buildOptions(QuestionBankDetailDto detail, String type) {
        ArrayNode options = objectMapper.createArrayNode();
        if ("true_false".equals(type)) {
            options.add(optionNode("TRUE", "A", "正确"));
            options.add(optionNode("FALSE", "B", "错误"));
            return options;
        }
        addOptionIfPresent(options, "A", detail.getOptionA());
        addOptionIfPresent(options, "B", detail.getOptionB());
        addOptionIfPresent(options, "C", detail.getOptionC());
        addOptionIfPresent(options, "D", detail.getOptionD());
        return options;
    }

    private void addOptionIfPresent(ArrayNode options, String key, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String content = raw.replaceFirst("^" + key + "[.、\\s]+", "").trim();
        options.add(optionNode(key, key, content));
    }

    private ObjectNode optionNode(String id, String label, String content) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("label", label);
        node.put("content", content);
        return node;
    }

    private String mapQuestionType(String raw) {
        if (raw == null) {
            return "single_choice";
        }
        String v = raw.toLowerCase();
        if (v.contains("multiple") || v.contains("multi")) {
            return "multiple_choice";
        }
        if (v.contains("judge") || v.contains("true") || v.contains("boolean")) {
            return "true_false";
        }
        if (v.contains("fill")) {
            return "fill_blank";
        }
        if (v.contains("code") || v.contains("program")) {
            return "coding";
        }
        return "single_choice";
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
}
