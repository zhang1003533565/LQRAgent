package com.lqragent.backend.prompt.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.prompt.entity.AgentPrompt;
import com.lqragent.backend.prompt.service.PromptService;

import lombok.RequiredArgsConstructor;

/**
 * Agent 提示词管理接口
 * 
 * 提供以下功能：
 * - GET /api/admin/prompts - 获取所有提示词
 * - GET /api/admin/prompts/{agentId} - 获取指定 Agent 提示词
 * - PUT /api/admin/prompts/{agentId} - 更新提示词
 * - POST /api/admin/prompts/{agentId}/reset - 重置为默认值
 */
@RestController
@RequestMapping("/api/admin/prompts")
@RequiredArgsConstructor
public class PromptAdminController {

    private final PromptService promptService;

    /**
     * 获取所有 Agent 提示词
     */
    @GetMapping
    public ResponseEntity<List<AgentPrompt>> getAllPrompts() {
        return ResponseEntity.ok(promptService.getAllPrompts());
    }

    /**
     * 获取指定 Agent 提示词
     */
    @GetMapping("/{agentId}")
    public ResponseEntity<AgentPrompt> getPrompt(@PathVariable String agentId) {
        return promptService.getPromptDetail(agentId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 更新提示词
     */
    @PutMapping("/{agentId}")
    public ResponseEntity<AgentPrompt> updatePrompt(
            @PathVariable String agentId,
            @RequestBody Map<String, String> request) {
        
        String content = request.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String updatedBy = request.getOrDefault("updatedBy", "admin");
        AgentPrompt updated = promptService.updatePrompt(agentId, content, updatedBy);
        return ResponseEntity.ok(updated);
    }

    /**
     * 重置为默认值
     */
    @PostMapping("/{agentId}/reset")
    public ResponseEntity<AgentPrompt> resetPrompt(@PathVariable String agentId) {
        try {
            AgentPrompt reset = promptService.resetPrompt(agentId);
            return ResponseEntity.ok(reset);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 清除缓存
     */
    @PostMapping("/clear-cache")
    public ResponseEntity<Map<String, String>> clearCache() {
        promptService.clearCache();
        return ResponseEntity.ok(Map.of("message", "缓存已清除"));
    }
}
