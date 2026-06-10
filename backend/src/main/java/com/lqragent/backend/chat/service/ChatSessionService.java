package com.lqragent.backend.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.entity.ChatSession.SessionStatus;
import com.lqragent.backend.chat.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;

    @Transactional
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title(title != null ? title : "新对话")
                .status(SessionStatus.ACTIVE)
                .build();
        ChatSession saved = chatSessionRepository.save(session);
        log.info("[ChatSession] created: id={}, userId={}", saved.getId(), userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<ChatSession> findById(Long sessionId) {
        return chatSessionRepository.findById(sessionId);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> findByUserId(Long userId) {
        return chatSessionRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, SessionStatus.ACTIVE);
    }

    @Transactional
    public void updateTitle(Long sessionId, String title) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            session.setTitle(title);
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(session);
        });
    }

    @Transactional
    public void updateAiServerSessionId(Long sessionId, String aiServerSessionId) {
        chatSessionRepository.findById(sessionId).ifPresent(session -> {
            // 将 aiServerSessionId 存储到 metadata 或其他字段
            session.setUpdatedAt(LocalDateTime.now());
            chatSessionRepository.save(session);
            log.info("[ChatSession] updated aiServerSessionId: sessionId={}, aiServerSessionId={}", sessionId, aiServerSessionId);
        });
    }
}
