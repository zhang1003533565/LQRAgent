package com.lqragent.backend.aiserver;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * AI 服务 HTTP 调用封装。
 * 所有对 ai-server 的 REST 调用都经过这里，前端不直接访问 ai-server。
 * 基地址从 sys_config / application.properties 动态读取。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final AppRuntimeConfig runtimeConfig;

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        return RestClient.builder()
                .baseUrl(runtimeConfig.getAiServerBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 探测 AI Server 是否可达。
     */
    public boolean ping() {
        String base = runtimeConfig.getAiServerBaseUrl();
        try {
            client().get()
                    .uri("/api/v1/knowledge/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("[AiServerClient] ping failed for {}: {}", base, e.getMessage());
            return false;
        }
    }

    /**
     * 创建知识库。
     */
    public Map<?, ?> createKnowledgeBase(String kbName) {
        log.info("[AiServerClient] createKnowledgeBase: {}", kbName);
        return client().post()
                .uri("/api/v1/knowledge/kb")
                .body(Map.of("name", kbName))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 上传文档到知识库。
     */
    public Map<?, ?> uploadDocument(String kbName, String fileName, byte[] content) {
        log.info("[AiServerClient] uploadDocument: kb={}, file={}", kbName, fileName);
        throw new UnsupportedOperationException("uploadDocument 待实现");
    }

    /**
     * 生成题目。
     */
    public Map<?, ?> generateQuestion(String kbName, String topic, int count) {
        log.info("[AiServerClient] generateQuestion: kb={}, topic={}, count={}", kbName, topic, count);
        return client().post()
                .uri("/api/v1/question/generate")
                .body(Map.of("kb_name", kbName, "topic", topic, "count", count))
                .retrieve()
                .body(Map.class);
    }

    /**
     * 生成讲义资源。
     */
    public Map<?, ?> generateLesson(String kbName, String knowledgePointId) {
        log.info("[AiServerClient] generateLesson: kb={}, kpId={}", kbName, knowledgePointId);
        return client().post()
                .uri("/api/v1/book/generate")
                .body(Map.of("kb_name", kbName, "topic", knowledgePointId))
                .retrieve()
                .body(Map.class);
    }
}
