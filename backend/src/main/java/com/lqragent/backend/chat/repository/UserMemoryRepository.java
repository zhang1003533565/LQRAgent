package com.lqragent.backend.chat.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.lqragent.backend.chat.entity.UserMemory;
import com.lqragent.backend.chat.entity.UserMemory.MemoryType;

/**
 * 用户记忆 Repository
 */
@Repository
public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {
    
    /**
     * 获取用户某类记忆
     */
    List<UserMemory> findByUserIdAndMemoryTypeOrderByImportanceDesc(Long userId, MemoryType type);
    
    /**
     * 获取用户重要记忆
     */
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.importance >= :minImportance ORDER BY m.importance DESC, m.lastAccessedAt DESC")
    List<UserMemory> findImportantMemories(@Param("userId") Long userId, @Param("minImportance") Integer minImportance);
    
    /**
     * 搜索记忆内容
     */
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId AND m.content LIKE %:keyword% ORDER BY m.importance DESC")
    List<UserMemory> searchMemories(@Param("userId") Long userId, @Param("keyword") String keyword);
    
    /**
     * 获取用户所有记忆
     */
    List<UserMemory> findByUserIdOrderByImportanceDesc(Long userId);
    
    /**
     * 获取用户最近访问的记忆
     */
    @Query("SELECT m FROM UserMemory m WHERE m.userId = :userId ORDER BY m.lastAccessedAt DESC LIMIT :limit")
    List<UserMemory> findRecentlyAccessed(@Param("userId") Long userId, @Param("limit") int limit);
}
