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

import com.lqragent.backend.chat.entity.UserMemory;
import com.lqragent.backend.chat.entity.UserMemory.MemoryType;
import com.lqragent.backend.chat.service.UserMemoryService;

import lombok.RequiredArgsConstructor;

/**
 * 用户记忆 Controller
 */
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MemoryController {

    private final UserMemoryService memoryService;

    /**
     * 获取用户的记忆列表
     */
    @GetMapping
    public ResponseEntity<List<UserMemory>> getMemories(
            @RequestParam Long userId,
            @RequestParam(required = false) MemoryType type) {
        if (type != null) {
            return ResponseEntity.ok(memoryService.getMemories(userId, type));
        }
        return ResponseEntity.ok(memoryService.getAllMemories(userId));
    }

    /**
     * 添加记忆
     */
    @PostMapping
    public ResponseEntity<UserMemory> addMemory(
            @RequestParam Long userId,
            @RequestBody Map<String, Object> body) {
        MemoryType type = MemoryType.valueOf((String) body.get("memoryType"));
        String content = (String) body.get("content");
        String source = (String) body.getOrDefault("source", "user_input");
        Integer importance = (Integer) body.getOrDefault("importance", 1);
        
        return ResponseEntity.ok(memoryService.addMemory(userId, type, content, source, importance));
    }

    /**
     * 更新记忆
     */
    @PutMapping("/{memoryId}")
    public ResponseEntity<UserMemory> updateMemory(
            @PathVariable Long memoryId,
            @RequestBody Map<String, Object> body) {
        String content = (String) body.get("content");
        Integer importance = (Integer) body.get("importance");
        
        return ResponseEntity.ok(memoryService.updateMemory(memoryId, content, importance));
    }

    /**
     * 删除记忆
     */
    @DeleteMapping("/{memoryId}")
    public ResponseEntity<Void> deleteMemory(@PathVariable Long memoryId) {
        memoryService.deleteMemory(memoryId);
        return ResponseEntity.ok().build();
    }

    /**
     * 搜索记忆
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserMemory>> searchMemories(
            @RequestParam Long userId,
            @RequestParam String keyword) {
        return ResponseEntity.ok(memoryService.searchMemories(userId, keyword));
    }

    /**
     * 获取用于 Prompt 的重要记忆
     */
    @GetMapping("/prompt")
    public ResponseEntity<Map<String, String>> getMemoriesForPrompt(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "3") int limit) {
        String memories = memoryService.getImportantMemoriesForPrompt(userId, limit);
        return ResponseEntity.ok(Map.of("memories", memories));
    }
}
