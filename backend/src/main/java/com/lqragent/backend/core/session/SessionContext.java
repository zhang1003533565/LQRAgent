package com.lqragent.backend.core.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话级上下文 — 隔离多用户/多轮推理的状态。
 * <p>
 * 每个 sessionId 独立存储推理消息列表，带 TTL 过期清理，避免静态 Map OOM。
 * </p>
 */
public class SessionContext {

    /** 默认 2 小时无访问则过期 */
    public static final long DEFAULT_TTL_MS = 2 * 60 * 60 * 1000L;

    private static final ConcurrentHashMap<String, SessionEntry> STORE = new ConcurrentHashMap<>();

    private SessionContext() {}

    private static final class SessionEntry {
        final List<Map<String, Object>> messages = new ArrayList<>();
        volatile long lastAccessAt = System.currentTimeMillis();
    }

    /** 获取或创建某会话的消息列表（与 AgentEngine 共享同一引用，调用方需同步写入） */
    public static List<Map<String, Object>> getMessages(String sessionId) {
        SessionEntry entry = STORE.computeIfAbsent(sessionId, k -> new SessionEntry());
        entry.lastAccessAt = System.currentTimeMillis();
        return entry.messages;
    }

    /** 追加消息 */
    public static void addMessage(String sessionId, Map<String, Object> message) {
        SessionEntry entry = STORE.computeIfAbsent(sessionId, k -> new SessionEntry());
        entry.lastAccessAt = System.currentTimeMillis();
        synchronized (entry.messages) {
            entry.messages.add(message);
        }
    }

    /** 重置会话（新一轮推理） */
    public static void reset(String sessionId) {
        STORE.remove(sessionId);
    }

    /** 整体清理（管理/测试用） */
    public static void clearAll() {
        STORE.clear();
    }

    /** 清理超过 TTL 未访问的会话，返回清理数量 */
    public static int evictExpired() {
        return evictExpired(DEFAULT_TTL_MS);
    }

    public static int evictExpired(long ttlMs) {
        long cutoff = System.currentTimeMillis() - ttlMs;
        int removed = 0;
        for (var it = STORE.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            if (e.getValue().lastAccessAt < cutoff) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }

    /** 当前活跃会话数 */
    public static int activeCount() {
        return STORE.size();
    }
}
