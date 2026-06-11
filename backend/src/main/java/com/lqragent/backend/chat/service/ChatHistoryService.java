package com.lqragent.backend.chat.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.entity.ChatSession.SessionStatus;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.repository.ChatSessionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天历史服务
 * 管理会话和消息的持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TITLE_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    /**
     * 创建新会话
     */
    @Transactional
    public ChatSession createSession(Long userId, String title) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title(title != null ? title : generateDefaultTitle())
                .status(SessionStatus.ACTIVE)
                .build();
        ChatSession saved = sessionRepo.save(session);
        log.info("[ChatHistory] created session: id={}, userId={}", saved.getId(), userId);
        return saved;
    }

    /**
     * 获取用户的会话列表
     */
    public List<ChatSession> getUserSessions(Long userId, int page, int size) {
        return sessionRepo.findActiveSessions(userId, PageRequest.of(page, size)).getContent();
    }

    /**
     * 获取用户最近的会话
     */
    public List<ChatSession> getRecentSessions(Long userId, int limit) {
        return sessionRepo.findRecentSessions(userId, limit);
    }

    /**
     * 搜索会话
     */
    public List<ChatSession> searchSessions(Long userId, String keyword) {
        return sessionRepo.searchByTitle(userId, keyword);
    }

    /**
     * 保存消息
     */
    @Transactional
    public ChatMessage saveMessage(Long sessionId, Long userId, String role, 
                                   String content, String agentName) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(role)
                .content(content)
                .agentName(agentName)
                .build();
        
        ChatMessage saved = messageRepo.save(message);

        // 更新会话
        ChatSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setUpdatedAt(LocalDateTime.now());
        if (session.getTitle() == null || session.getTitle().startsWith("新对话")) {
            session.setTitle(generateTitleFromContent(content));
        }
        sessionRepo.save(session);

        log.debug("[ChatHistory] saved message: sessionId={}, role={}", sessionId, role);
        return saved;
    }

    /**
     * 保存消息（带 metadata）
     */
    @Transactional
    public ChatMessage saveMessage(Long sessionId, Long userId, String role,
                                   String content, String agentName, String metadata) {
        ChatMessage message = ChatMessage.builder()
                .sessionId(sessionId)
                .userId(userId)
                .role(role)
                .content(content)
                .agentName(agentName)
                .metadata(metadata)
                .build();
        
        ChatMessage saved = messageRepo.save(message);

        // 更新会话
        ChatSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setUpdatedAt(LocalDateTime.now());
        if (session.getTitle() == null || session.getTitle().startsWith("新对话")) {
            session.setTitle(generateTitleFromContent(content));
        }
        sessionRepo.save(session);

        return saved;
    }

    /**
     * 获取会话的消息历史
     */
    public List<ChatMessage> getSessionMessages(Long sessionId, int limit) {
        if (limit > 0) {
            return messageRepo.findRecentBySession(sessionId, PageRequest.of(0, limit));
        }
        return messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
    }

    /**
     * 加载会话上下文（供 Agent 使用）
     */
    public String loadSessionContext(Long sessionId, int messageCount) {
        List<ChatMessage> messages = getSessionMessages(sessionId, messageCount);
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取会话消息数量
     */
    public long getMessageCount(Long sessionId) {
        return messageRepo.countBySessionId(sessionId);
    }

    /**
     * 归档会话
     */
    @Transactional
    public void archiveSession(Long sessionId) {
        ChatSession session = sessionRepo.findById(sessionId).orElseThrow();
        session.setStatus(SessionStatus.ARCHIVED);
        sessionRepo.save(session);
        log.info("[ChatHistory] archived session: {}", sessionId);
    }

    /**
     * 删除会话（硬删除，同时删除关联的消息）
     */
    @Transactional
    public void deleteSession(Long sessionId) {
        // 先删除关联的消息
        messageRepo.deleteBySessionId(sessionId);
        log.info("[ChatHistory] deleted messages for session: {}", sessionId);
        
        // 再删除会话
        sessionRepo.deleteById(sessionId);
        log.info("[ChatHistory] deleted session: {}", sessionId);
    }

    private String generateDefaultTitle() {
        return "新对话 " + LocalDateTime.now().format(TITLE_FORMAT);
    }

    private String generateTitleFromContent(String content) {
        if (content == null || content.isEmpty()) {
            return generateDefaultTitle();
        }
        // 去除换行符
        String clean = content.replaceAll("\\n", " ").trim();
        return clean.length() > 20 ? clean.substring(0, 20) + "..." : clean;
    }

    /**
     * 更新消息 metadata（用于保存图片、图表等附加信息）
     */
    @Transactional
    public ChatMessage updateMessageMetadata(Long messageId, Map<String, Object> metadata) {
        ChatMessage message = messageRepo.findById(messageId).orElseThrow();
        
        // 合并现有 metadata
        String existingMetadata = message.getMetadata();
        Map<String, Object> mergedMetadata = new java.util.HashMap<>();
        
        if (existingMetadata != null && !existingMetadata.isBlank()) {
            try {
                mergedMetadata = objectMapper.readValue(existingMetadata, Map.class);
            } catch (Exception e) {
                log.warn("[ChatHistory] failed to parse existing metadata: {}", e.getMessage());
            }
        }
        
        // 合并新 metadata
        mergedMetadata.putAll(metadata);
        
        // 保存
        try {
            message.setMetadata(objectMapper.writeValueAsString(mergedMetadata));
        } catch (Exception e) {
            log.warn("[ChatHistory] failed to serialize metadata: {}", e.getMessage());
        }
        
        // 如果 metadata 中有 contentType，也更新到实体字段
        if (metadata.containsKey("contentType")) {
            message.setContentType((String) metadata.get("contentType"));
        }
        
        return messageRepo.save(message);
    }
}
