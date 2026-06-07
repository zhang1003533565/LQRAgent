package com.lqragent.backend.agents.base;

import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 上下文记忆
 * 存储每个用户的对话历史，供 Agent 参考
 */
@Component
public class AgentMemory {
    
    // 用户对话历史：userId -> 消息列表
    private final Map<Long, List<MemoryEntry>> userMemories = new ConcurrentHashMap<>();
    
    private static final int MAX_MEMORY_SIZE = 20; // 每个用户最多存储 20 条
    
    @Data
    public static class MemoryEntry {
        private final String role;      // user / assistant
        private final String content;   // 消息内容
        private final String agent;     // 处理的 Agent
        private final long timestamp;
        
        public MemoryEntry(String role, String content, String agent) {
            this.role = role;
            this.content = content;
            this.agent = agent;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * 记录用户消息
     */
    public void addUserMessage(Long userId, String content) {
        userMemories.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new MemoryEntry("user", content, null));
        trimMemory(userId);
    }
    
    /**
     * 记录 Agent 回复
     */
    public void addAgentResponse(Long userId, String content, String agent) {
        userMemories.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new MemoryEntry("assistant", content, agent));
        trimMemory(userId);
    }
    
    /**
     * 获取用户最近的对话历史
     */
    public List<MemoryEntry> getRecentHistory(Long userId, int count) {
        List<MemoryEntry> memories = userMemories.getOrDefault(userId, List.of());
        int start = Math.max(0, memories.size() - count);
        return memories.subList(start, memories.size());
    }
    
    /**
     * 获取格式化的对话历史（用于 LLM prompt）
     */
    public String getFormattedHistory(Long userId, int count) {
        List<MemoryEntry> history = getRecentHistory(userId, count);
        if (history.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("最近的对话历史：\n");
        for (MemoryEntry entry : history) {
            if ("user".equals(entry.getRole())) {
                sb.append("用户: ").append(entry.getContent()).append("\n");
            } else {
                sb.append("助手: ").append(truncate(entry.getContent(), 100)).append("\n");
            }
        }
        return sb.toString();
    }
    
    /**
     * 清除用户记忆
     */
    public void clearMemory(Long userId) {
        userMemories.remove(userId);
    }
    
    private void trimMemory(Long userId) {
        List<MemoryEntry> memories = userMemories.get(userId);
        if (memories != null && memories.size() > MAX_MEMORY_SIZE) {
            userMemories.put(userId, new ArrayList<>(memories.subList(memories.size() - MAX_MEMORY_SIZE, memories.size())));
        }
    }
    
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        text = text.replaceAll("\\n", " ").replaceAll("\n", " ");
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
