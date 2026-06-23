package com.lqragent.backend.agents.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.service.ChatHistoryService;
import com.lqragent.backend.chat.service.UserMemoryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Agent 上下文记忆
 * 支持短期记忆（内存）和长期记忆（数据库）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentMemory {
    
    private final ChatHistoryService historyService;
    private final UserMemoryService memoryService;
    
    // 用户对话历史：userId -> 消息列表（短期记忆，内存）
    private final Map<Long, List<MemoryEntry>> userMemories = new ConcurrentHashMap<>();
    
    private static final int MAX_MEMORY_SIZE = 20; // 每个用户最多存储 20 条
    
    @lombok.Data
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
     * 记录用户消息（同时持久化）
     */
    public void addUserMessage(Long userId, String content) {
        addUserMessage(userId, null, content);
    }
    
    /**
     * 记录用户消息（带 sessionId 持久化）
     */
    public void addUserMessage(Long userId, Long sessionId, String content) {
        // 1. 保存到短期记忆
        userMemories.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new MemoryEntry("user", content, null));
        trimMemory(userId);
        
        // 2. 持久化到数据库
        if (sessionId != null) {
            try {
                historyService.saveMessage(sessionId, userId, "user", content, null);
            } catch (Exception e) {
                log.warn("[AgentMemory] save user message failed: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 记录 Agent 回复（同时持久化）
     */
    public void addAgentResponse(Long userId, String content, String agent) {
        addAgentResponse(userId, null, content, agent);
    }
    
    /**
     * 记录 Agent 回复（带 sessionId 持久化）
     */
    public void addAgentResponse(Long userId, Long sessionId, String content, String agent) {
        addAgentResponse(userId, sessionId, content, agent, null);
    }

    /**
     * 记录 Agent 回复（带 metadata，如 ragSources / imageUrl）
     * @return 持久化后的消息 ID，未持久化时返回 null
     */
    public Long addAgentResponse(Long userId, Long sessionId, String content, String agent,
                                 Map<String, Object> metadata) {
        userMemories.computeIfAbsent(userId, k -> new ArrayList<>())
                .add(new MemoryEntry("assistant", content, agent));
        trimMemory(userId);

        if (sessionId != null) {
            try {
                ChatMessage saved;
                if (metadata != null && !metadata.isEmpty()) {
                    saved = historyService.saveMessage(sessionId, userId, "assistant", content, agent, metadata);
                } else {
                    saved = historyService.saveMessage(sessionId, userId, "assistant", content, agent);
                }
                return saved.getId();
            } catch (Exception e) {
                log.warn("[AgentMemory] save agent response failed: {}", e.getMessage());
            }
        }
        return null;
    }
    
    /**
     * 获取上下文（短期记忆 + 重要长期记忆）
     */
    public String getContext(Long userId, Long sessionId) {
        StringBuilder context = new StringBuilder();
        
        // 1. 添加长期重要记忆
        try {
            String longTerm = memoryService.getImportantMemoriesForPrompt(userId, 3);
            if (!longTerm.isEmpty()) {
                context.append(longTerm).append("\n");
            }
        } catch (Exception e) {
            log.warn("[AgentMemory] get long-term memory failed: {}", e.getMessage());
        }
        
        // 2. 添加短期对话历史
        String shortTerm = getFormattedHistory(userId, 10);
        if (!shortTerm.isEmpty()) {
            context.append(shortTerm);
        }
        
        return context.toString();
    }
    
    /**
     * 加载历史会话到短期记忆
     */
    public void loadSession(Long userId, Long sessionId) {
        try {
            List<ChatMessage> messages = historyService.getSessionMessages(sessionId, 20);
            List<MemoryEntry> entries = messages.stream()
                    .map(m -> new MemoryEntry(m.getRole(), m.getContent(), m.getAgentName()))
                    .collect(Collectors.toList());
            userMemories.put(userId, entries);
        } catch (Exception e) {
            log.warn("[AgentMemory] load session failed: {}", e.getMessage());
        }
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
     * 清除用户记忆（内存 + 数据库）
     */
    public void clearMemory(Long userId) {
        // 清除内存中的短期记忆
        userMemories.remove(userId);
        // 清除数据库中的长期记忆
        try {
            memoryService.deleteAllMemories(userId);
        } catch (Exception e) {
            log.warn("[AgentMemory] clear database memory failed: {}", e.getMessage());
        }
    }
    
    private void trimMemory(Long userId) {
        List<MemoryEntry> memories = userMemories.get(userId);
        if (memories != null && memories.size() > MAX_MEMORY_SIZE) {
            userMemories.put(userId, new ArrayList<>(memories.subList(memories.size() - MAX_MEMORY_SIZE, memories.size())));
        }
    }
    
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        text = text.replaceAll("\n", " ").replaceAll("\n", " ");
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
