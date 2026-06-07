package com.lqragent.backend.agents.learnerprofile.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM 画像抽取器。
 * 从最近 N 条对话记录中，调 LLM 提取 6 维画像。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileExtractor {

    private final AiServerWsProxy aiServerWsProxy;

    /**
     * 从对话记录中抽取画像 6 维度。
     * @param messages 最近 N 条对话（按时间正序）
     * @return JSON 字符串：{knowledge_base, learning_goal, cognitive_style, weakness, learning_pace, interest}
     *         失败时返回 null
     */
    public String extract(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            log.warn("[ProfileExtractor] 对话历史为空，跳过抽取");
            return null;
        }

        // 拼接对话摘要
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            String who = "user".equals(m.getRole()) ? "学生" : "AI";
            sb.append(who).append(": ").append(m.getContent()).append("\n");
        }
        String dialogSummary = sb.toString();

        if (dialogSummary.length() > 2000) {
            dialogSummary = dialogSummary.substring(dialogSummary.length() - 2000);
        }

        // 调 LLM 抽取
        String result = aiServerWsProxy.extractProfile(dialogSummary);
        if (result != null) {
            log.info("[ProfileExtractor] 画像抽取成功, len={}", result.length());
        } else {
            log.warn("[ProfileExtractor] 画像抽取失败（LLM 返回空）");
        }
        return result;
    }
}
