package com.lqragent.backend.chat.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lqragent.backend.chat.entity.UserMemory;
import com.lqragent.backend.chat.entity.UserMemory.MemoryType;
import com.lqragent.backend.chat.repository.UserMemoryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户记忆服务
 * 管理用户的长期记忆
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserMemoryService {

    private final UserMemoryRepository memoryRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "memory:long:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);
    /** 重要记忆的最低 importance 阈值，默认为 3 */
    private static final int DEFAULT_MIN_IMPORTANCE = 3;

    /**
     * 添加记忆
     */
    @Transactional
    public UserMemory addMemory(Long userId, MemoryType type, String content, String source) {
        UserMemory memory = UserMemory.builder()
                .userId(userId)
                .memoryType(type)
                .content(content)
                .source(source)
                .importance(1)
                .accessCount(0)
                .build();
        
        UserMemory saved = memoryRepo.save(memory);
        evictMemoryCache(userId, type);
        log.info("[Memory] added: userId={}, type={}, id={}", userId, type, saved.getId());
        return saved;
    }

    /**
     * 添加记忆（带重要程度）
     */
    @Transactional
    public UserMemory addMemory(Long userId, MemoryType type, String content, 
                                String source, int importance) {
        UserMemory memory = UserMemory.builder()
                .userId(userId)
                .memoryType(type)
                .content(content)
                .source(source)
                .importance(importance)
                .accessCount(0)
                .build();
        
        UserMemory saved = memoryRepo.save(memory);
        evictMemoryCache(userId, type);
        return saved;
    }

    /**
     * 获取用户某类记忆
     */
    public List<UserMemory> getMemories(Long userId, MemoryType type) {
        String cacheKey = CACHE_PREFIX + userId + ":" + type.name();
        @SuppressWarnings("unchecked")
        List<UserMemory> cached = (List<UserMemory>) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        List<UserMemory> memories = memoryRepo.findByUserIdAndMemoryTypeOrderByImportanceDesc(userId, type);
        redisTemplate.opsForValue().set(cacheKey, memories, CACHE_TTL);
        return memories;
    }

    /**
     * 获取用户所有记忆
     */
    public List<UserMemory> getAllMemories(Long userId) {
        return memoryRepo.findByUserIdOrderByImportanceDesc(userId);
    }

    /**
     * 获取重要记忆（用于 Prompt 注入）
     */
    public String getImportantMemoriesForPrompt(Long userId, int limit) {
        List<UserMemory> memories = memoryRepo.findImportantMemories(userId, DEFAULT_MIN_IMPORTANCE);
        if (memories.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder("用户相关记忆：\n");
        int count = 0;
        for (UserMemory m : memories) {
            if (count >= limit) break;
            sb.append("- ").append(m.getContent()).append("\n");
            // 更新访问计数
            m.setAccessCount(m.getAccessCount() + 1);
            m.setLastAccessedAt(LocalDateTime.now());
            count++;
        }
        memoryRepo.saveAll(memories);
        
        return sb.toString();
    }

    /**
     * 从聊天中提取记忆（异步）
     */
    @Async
    public void extractMemoriesFromChat(Long userId, String userMessage, String agentResponse) {
        try {
            // 简单规则提取（后续可集成 LLM 增强）
            String lowerMsg = userMessage.toLowerCase();
            
            // 检测偏好
            if (lowerMsg.contains("喜欢") || lowerMsg.contains("偏好") || lowerMsg.contains("prefer")) {
                addMemory(userId, MemoryType.PREFERENCE, userMessage, "auto_extract", 2);
            }
            
            // 检测兴趣
            if (lowerMsg.contains("想学") || lowerMsg.contains("learn") || lowerMsg.contains("了解")) {
                addMemory(userId, MemoryType.TOPIC_INTEREST, userMessage, "auto_extract", 2);
            }
            
            // 检测交互风格
            if (lowerMsg.contains("详细") || lowerMsg.contains("简单") || lowerMsg.contains("举例")) {
                addMemory(userId, MemoryType.INTERACTION_STYLE, userMessage, "auto_extract", 1);
            }
            
        } catch (Exception e) {
            log.warn("[Memory] extractMemoriesFromChat failed: {}", e.getMessage());
        }
    }

    /**
     * 搜索记忆
     */
    public List<UserMemory> searchMemories(Long userId, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return memoryRepo.findByUserIdOrderByImportanceDesc(userId);
        }
        return memoryRepo.searchMemories(userId, keyword);
    }

    /**
     * 更新记忆
     */
    @Transactional
    public UserMemory updateMemory(Long memoryId, String content, Integer importance) {
        UserMemory memory = memoryRepo.findById(memoryId).orElseThrow();
        if (content != null) {
            memory.setContent(content);
        }
        if (importance != null) {
            memory.setImportance(importance);
        }
        UserMemory saved = memoryRepo.save(memory);
        evictMemoryCache(memory.getUserId(), memory.getMemoryType());
        return saved;
    }

    /**
     * 删除记忆
     */
    @Transactional
    public void deleteMemory(Long memoryId) {
        UserMemory memory = memoryRepo.findById(memoryId).orElseThrow();
        memoryRepo.deleteById(memoryId);
        evictMemoryCache(memory.getUserId(), memory.getMemoryType());
        log.info("[Memory] deleted: id={}, userId={}", memoryId, memory.getUserId());
    }

    /**
     * 删除用户所有记忆
     */
    @Transactional
    public void deleteAllMemories(Long userId) {
        List<UserMemory> memories = memoryRepo.findByUserIdOrderByImportanceDesc(userId);
        memoryRepo.deleteAll(memories);
        // 清除所有类型的缓存
        for (MemoryType type : MemoryType.values()) {
            evictMemoryCache(userId, type);
        }
        log.info("[Memory] deleted all memories for userId={}", userId);
    }

    private void evictMemoryCache(Long userId, MemoryType type) {
        redisTemplate.delete(CACHE_PREFIX + userId + ":" + type.name());
    }
}
