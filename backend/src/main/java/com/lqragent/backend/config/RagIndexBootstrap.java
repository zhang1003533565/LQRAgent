package com.lqragent.backend.config;

import java.util.List;
import java.util.Map;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.lqragent.backend.chat.proxy.AiServerClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 启动后检查知识库是否已写入 Docker Chroma，缺失则异步触发 reindex。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexBootstrap {

    private final AiServerClient aiServerClient;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void syncChromaIndexes() {
        try {
            if (!aiServerClient.ping()) {
                log.warn("[RagIndexBootstrap] ai-server unavailable, skip Chroma sync");
                return;
            }
            List<Map<String, Object>> kbs = aiServerClient.listKnowledgeBases();
            for (Map<String, Object> kb : kbs) {
                Object nameObj = kb.get("name");
                if (nameObj == null) {
                    continue;
                }
                String kbName = String.valueOf(nameObj);
                Map<String, Object> probe = aiServerClient.searchKnowledgeBase(kbName, "ping", 1);
                if (Boolean.TRUE.equals(probe.get("needs_reindex"))) {
                    log.info("[RagIndexBootstrap] KB '{}' missing Chroma index, triggering reindex", kbName);
                    aiServerClient.reindexKnowledgeBase(kbName);
                }
            }
        } catch (Exception e) {
            log.warn("[RagIndexBootstrap] Chroma sync skipped: {}", e.getMessage());
        }
    }
}
