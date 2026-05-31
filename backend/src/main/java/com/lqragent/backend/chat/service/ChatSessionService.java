package com.lqragent.backend.chat.service;

import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    @Transactional
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(title != null ? title : "新对话")
                .build();
        ChatSession saved = chatSessionRepository.save(session);
        log.info("[ChatSession] created: id={}, userId={}", saved.getId(), userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ChatSession> findById(String sessionId) {
        return chatSessionRepository.findById(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> findByUserId(Long userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public void updateAiServerSessionId(String sessionId, String aiServerSessionId) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setAiServerSessionId(aiServerSessionId);
            chatSessionRepository.save(session);
        });
    }

    @Transactional
    public void updateTitle(String sessionId, String title) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setTitle(title);
            chatSessionRepository.save(session);
        });
    }

    @Transactional
    public boolean deleteSession(String sessionId) {
        return chatSessionRepository.findById(sessionId).map(session -> {
            chatSessionRepository.delete(session);
            log.info("[ChatSession] deleted: id={}", sessionId);
            return true;
        }).orElse(false);
    }
}
