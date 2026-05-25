package com.lqragent.backend.chat.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器。
 * 追踪所有活跃连接，提供按 userId 推送事件能力。
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** wsSessionId → (userId, username) */
    private final Map<String, UserInfo> sessionUsers = new ConcurrentHashMap<>();
    /** wsSessionId → WebSocketSession */
    private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
    /** userId → wsSessionId（最新连接） */
    private final Map<Long, String> userSessions = new ConcurrentHashMap<>();

    public UserInfo getUserInfo(String sessionId) {
        return sessionUsers.get(sessionId);
    }

    public void register(WebSocketSession session, Long userId, String username) {
        sessionUsers.put(session.getId(), new UserInfo(userId, username));
        wsSessions.put(session.getId(), session);
        userSessions.put(userId, session.getId());
    }

    public void unregister(String sessionId) {
        UserInfo info = sessionUsers.remove(sessionId);
        wsSessions.remove(sessionId);
        if (info != null) {
            userSessions.remove(info.userId(), sessionId);
        }
    }

    /** 向指定用户推送事件（若该用户有活跃连接） */
    public boolean sendToUser(Long userId, String type, String content) {
        String sessionId = userSessions.get(userId);
        if (sessionId == null) return false;
        WebSocketSession session = wsSessions.get(sessionId);
        if (session == null || !session.isOpen()) return false;
        sendEvent(session, type, content);
        return true;
    }

    /** 向指定用户推送 JSON 事件 */
    public boolean sendToUser(Long userId, String type, com.fasterxml.jackson.databind.JsonNode contentNode) {
        String sessionId = userSessions.get(userId);
        if (sessionId == null) return false;
        WebSocketSession session = wsSessions.get(sessionId);
        if (session == null || !session.isOpen()) return false;
        try {
            String payload = objectMapper.createObjectNode()
                    .put("type", type)
                    .set("payload", contentNode)
                    .toString();
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
            return true;
        } catch (IOException e) {
            log.error("[WS] sendToUser error: userId={}, type={}", userId, type, e);
            return false;
        }
    }

    public boolean isOnline(Long userId) {
        String sessionId = userSessions.get(userId);
        if (sessionId == null) return false;
        WebSocketSession session = wsSessions.get(sessionId);
        return session != null && session.isOpen();
    }

    private void sendEvent(WebSocketSession session, String type, String content) {
        try {
            String payload;
            try {
                com.fasterxml.jackson.databind.JsonNode contentNode = objectMapper.readTree(content);
                payload = objectMapper.createObjectNode()
                        .put("type", type)
                        .setAll((com.fasterxml.jackson.databind.node.ObjectNode) contentNode)
                        .toString();
            } catch (Exception e) {
                payload = objectMapper.createObjectNode()
                        .put("type", type)
                        .put("content", content)
                        .toString();
            }
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.error("[WS] send error: sessionId={}, type={}", session.getId(), type, e);
        }
    }

    public record UserInfo(Long userId, String username) {}
}
