package com.lqragent.backend.chat.repository;

import com.lqragent.backend.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);
}
