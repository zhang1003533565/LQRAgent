package com.lqragent.backend.chat.repository;

import com.lqragent.backend.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    List<ChatMessage> findBySessionIdOrderByCreatedAtDesc(String sessionId, Pageable pageable);
}
