package com.lqragent.backend.chat.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.lqragent.backend.chat.entity.ChatMessage;

/**
 * 聊天消息 Repository
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    /**
     * 获取会话的所有消息（按时间正序）
     */
    List<ChatMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
    
    /**
     * 获取会话最近的消息（倒序分页）
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.sessionId = :sessionId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentBySession(@Param("sessionId") Long sessionId, Pageable pageable);
    
    /**
     * 获取用户在指定会话的消息数量
     */
    long countBySessionId(Long sessionId);
    
    /**
     * 获取用户所有会话的消息总数
     */
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.userId = :userId")
    long countByUserId(@Param("userId") Long userId);
}
