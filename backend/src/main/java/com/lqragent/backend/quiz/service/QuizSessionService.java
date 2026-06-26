package com.lqragent.backend.quiz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.common.exception.BusinessException;
import com.lqragent.backend.quiz.dto.QuizPreferencesDto;
import com.lqragent.backend.quiz.entity.QuizPracticeSession;
import com.lqragent.backend.quiz.entity.QuizQuestionFavorite;
import com.lqragent.backend.quiz.entity.QuizQuestionMark;
import com.lqragent.backend.quiz.repository.QuizPracticeSessionRepository;
import com.lqragent.backend.quiz.repository.QuizQuestionFavoriteRepository;
import com.lqragent.backend.quiz.repository.QuizQuestionMarkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuizSessionService {

    private final QuizPracticeSessionRepository sessionRepository;
    private final QuizQuestionFavoriteRepository favoriteRepository;
    private final QuizQuestionMarkRepository markRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public JsonNode saveSession(Long userId, JsonNode sessionPayload) {
        String sessionId = readText(sessionPayload, "id");
        if (sessionId == null || sessionId.isBlank()) {
            throw BusinessException.of("会话 id 不能为空");
        }

        String status = readText(sessionPayload, "status");
        String sessionJson = writeJson(sessionPayload);

        QuizPracticeSession entity = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElse(QuizPracticeSession.builder().id(sessionId).userId(userId).build());
        entity.setSessionData(sessionJson);
        entity.setStatus(status);
        sessionRepository.save(entity);

        return sessionPayload;
    }

    public JsonNode getSession(Long userId, String sessionId) {
        QuizPracticeSession entity = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("练习会话不存在"));
        return readJson(entity.getSessionData());
    }

    public List<JsonNode> listSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(item -> readJson(item.getSessionData()))
                .toList();
    }

    @Transactional
    public void deleteSession(Long userId, String sessionId) {
        QuizPracticeSession entity = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> BusinessException.notFound("练习会话不存在"));
        sessionRepository.delete(entity);
    }

    public QuizPreferencesDto getPreferences(Long userId) {
        List<Long> favorites = favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(QuizQuestionFavorite::getQuestionId)
                .toList();
        List<Long> marks = markRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(QuizQuestionMark::getQuestionId)
                .toList();
        return QuizPreferencesDto.builder()
                .favoriteQuestionIds(favorites)
                .markedQuestionIds(marks)
                .build();
    }

    @Transactional
    public void setFavorite(Long userId, Long questionId, boolean favorite) {
        if (favorite) {
            if (favoriteRepository.findByUserIdAndQuestionId(userId, questionId).isEmpty()) {
                favoriteRepository.save(QuizQuestionFavorite.builder()
                        .userId(userId)
                        .questionId(questionId)
                        .build());
            }
            return;
        }
        favoriteRepository.deleteByUserIdAndQuestionId(userId, questionId);
    }

    @Transactional
    public void setMarked(Long userId, Long questionId, boolean marked) {
        if (marked) {
            if (markRepository.findByUserIdAndQuestionId(userId, questionId).isEmpty()) {
                markRepository.save(QuizQuestionMark.builder()
                        .userId(userId)
                        .questionId(questionId)
                        .build());
            }
            return;
        }
        markRepository.deleteByUserIdAndQuestionId(userId, questionId);
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw BusinessException.of("会话数据损坏");
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw BusinessException.of("会话数据无法序列化");
        }
    }

    private String readText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
