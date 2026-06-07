package com.lqragent.backend.chat.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.service.ChatHistoryService;

import lombok.RequiredArgsConstructor;

/**
 * 聊天历史 Controller
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ChatHistoryService chatHistoryService;

    /**
     * 获取用户的会话列表
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSession>> getSessions(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(chatHistoryService.getUserSessions(userId, page, size));
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
    public ResponseEntity<ChatSession> createSession(
            @RequestParam Long userId,
            @RequestBody(required = false) Map<String, String> body) {
        String title = body != null ? body.get("title") : null;
        return ResponseEntity.ok(chatHistoryService.createSession(userId, title));
    }

    /**
     * 获取会话的消息列表
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessage>> getMessages(
            @PathVariable Long sessionId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(chatHistoryService.getSessionMessages(sessionId, limit));
    }

    /**
     * 删除会话（软删除）
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId) {
        chatHistoryService.deleteSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 归档会话
     */
    @PutMapping("/sessions/{sessionId}/archive")
    public ResponseEntity<Void> archiveSession(@PathVariable Long sessionId) {
        chatHistoryService.archiveSession(sessionId);
        return ResponseEntity.ok().build();
    }

    /**
     * 搜索会话
     */
    @GetMapping("/sessions/search")
    public ResponseEntity<List<ChatSession>> searchSessions(
            @RequestParam Long userId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(chatHistoryService.searchSessions(userId, keyword));
    }
}
