package com.lqragent.backend.chat.proxy;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * AI 服务 HTTP 调用封装。
 * 基地址从 sys_config / application.properties 动态读取。
 * 路径对齐 DeepTutor 实际 REST API。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final AppRuntimeConfig runtimeConfig;

    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return RestClient.builder()
                .baseUrl(runtimeConfig.getAiServerBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /** 探测 AI Server 是否可达 */
    public boolean ping() {
        try {
            client().get().uri("/api/v1/knowledge/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("[AiServerClient] ping 失败: {}", e.getMessage());
            return false;
        }
    }

    /** 列出知识库 */
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> listKnowledgeBases() {
        return client().get()
                .uri("/api/v1/knowledge/list")
                .retrieve()
                .body(java.util.List.class);
    }

    /** 创建知识库 */
    public Map<?, ?> createKnowledgeBase(String name) {
        log.info("[AiServerClient] createKnowledgeBase: {}", name);
        return client().post()
                .uri("/api/v1/knowledge/create")
                .body(Map.of("name", name, "files", java.util.Collections.emptyList()))
                .retrieve()
                .body(Map.class);
    }

    /** 上传文档到知识库 */
    public Map<?, ?> uploadDocument(String kbName, String fileName, byte[] content, String mimeType) {
        log.info("[AiServerClient] uploadDocument: kb={}, file={}", kbName, fileName);
        // DeepTutor 知识库上传走 multipart，这里用 RestClient 的 body 模拟
        return client().post()
                .uri("/api/v1/knowledge/{kb_name}/upload", kbName)
                .header("Content-Type", mimeType)
                .body(content)
                .retrieve()
                .body(Map.class);
    }

    /** 获取画像记忆（DeepTutor Persistent Memory） */
    public Map<?, ?> getMemory(Long userId) {
        return client().get()
                .uri("/api/v1/memory")
                .retrieve()
                .body(Map.class);
    }

    /** 更新画像记忆 */
    public Map<?, ?> updateMemory(Long userId, Map<String, Object> payload) {
        return client().post()
                .uri("/api/v1/memory")
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

}
