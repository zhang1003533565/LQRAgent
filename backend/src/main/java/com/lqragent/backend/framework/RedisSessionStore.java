package com.lqragent.backend.framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 会话存储 — SessionContext 的持久化后端。
 * <p>
 * 将 AgentEngine 推理循环的消息列表持久化到 Redis，
 * 支持断电恢复和跨实例共享。TTL 2 小时自动过期。
 * </p>
 */
@Slf4j
@Component
public class RedisSessionStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String KEY_PREFIX = "agent:session:";
    private static final long TTL_HOURS = 2;

    public RedisSessionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取会话消息列表。
     * 从 Redis LIST 中反序列化所有消息。
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMessages(String sessionId) {
        try {
            String key = KEY_PREFIX + sessionId;
            List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
            if (raw == null || raw.isEmpty()) {
                return new ArrayList<>();
            }
            List<Map<String, Object>> messages = new ArrayList<>();
            for (String json : raw) {
                messages.add(objectMapper.readValue(json, Map.class));
            }
            // 续期
            redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
            return messages;
        } catch (Exception e) {
            log.warn("[RedisSessionStore] 读取失败: sessionId={}, error={}", sessionId, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 追加一条消息到会话。
     */
    public void addMessage(String sessionId, Map<String, Object> message) {
        try {
            String key = KEY_PREFIX + sessionId;
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.warn("[RedisSessionStore] 序列化失败: sessionId={}", sessionId);
        } catch (Exception e) {
            log.warn("[RedisSessionStore] 写入失败: sessionId={}, error={}", sessionId, e.getMessage());
        }
    }

    /**
     * 批量写入消息（用于初始化会话）。
     */
    public void initMessages(String sessionId, List<Map<String, Object>> messages) {
        try {
            String key = KEY_PREFIX + sessionId;
            redisTemplate.delete(key);
            for (Map<String, Object> msg : messages) {
                String json = objectMapper.writeValueAsString(msg);
                redisTemplate.opsForList().rightPush(key, json);
            }
            redisTemplate.expire(key, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("[RedisSessionStore] 批量写入失败: sessionId={}", sessionId);
        }
    }

    /**
     * 清空会话。
     */
    public void reset(String sessionId) {
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("[RedisSessionStore] 清空失败: sessionId={}", sessionId);
        }
    }

    /**
     * 检查会话是否存在。
     */
    public boolean exists(String sessionId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + sessionId));
        } catch (Exception e) {
            return false;
        }
    }
}
