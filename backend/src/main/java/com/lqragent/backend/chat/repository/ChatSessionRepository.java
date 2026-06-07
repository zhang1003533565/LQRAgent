package com.lqragent.backend.chat.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.entity.ChatSession.SessionStatus;

/**
 * 聊天会话 Repository
 */
@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    
    /**
     * 获取用户的活跃会话列表
     */
    List<ChatSession> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, SessionStatus status);
    
    /**
     * 分页获取用户活跃会话
     */
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status = 'ACTIVE' ORDER BY s.updatedAt DESC")
    Page<ChatSession> findActiveSessions(@Param("userId") Long userId, Pageable pageable);
    
    /**
     * 获取用户最近的会话
     */
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.status <> 'DELETED' ORDER BY s.updatedAt DESC LIMIT :limit")
    List<ChatSession> findRecentSessions(@Param("userId") Long userId, @Param("limit") int limit);
    
    /**
     * 搜索会话标题
     */
    @Query("SELECT s FROM ChatSession s WHERE s.userId = :userId AND s.title LIKE %:keyword% AND s.status <> 'DELETED' ORDER BY s.updatedAt DESC")
    List<ChatSession> searchByTitle(@Param("userId") Long userId, @Param("keyword") String keyword);
}
