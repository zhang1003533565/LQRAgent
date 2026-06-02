package com.lqragent.backend.chat.proxy;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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

    /**
     * 创建带超时配置的 RestClient（connect=5s, read=30s）。
     */
    private RestClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return RestClient.builder()
                .baseUrl(runtimeConfig.getAiServerBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * 创建用于大文件上传的 RestClient（connect=5s, read=120s）。
     */
    private RestClient uploadClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        return RestClient.builder()
                .baseUrl(runtimeConfig.getAiServerBaseUrl())
                .requestFactory(factory)
                .build();
    }

    // ==================== 基础探测 ====================

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

    /** 获取系统完整状态（LLM / Embedding / Search 连通性） */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSystemStatus() {
        try {
            return client().get()
                    .uri("/api/v1/system/status")
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("[AiServerClient] system status 失败: {}", e.getMessage());
            return Map.of();
        }
    }

    // ==================== Embedding 配置 ====================

    /**
     * 测试 Embedding 连通性。
     * 调用后 ai-server 会自动填充 embedding 维度到 model_catalog.json。
     */
    public boolean testEmbedding() {
        try {
            var resp = client().post()
                    .uri("/api/v1/system/test/embeddings")
                    .retrieve()
                    .toBodilessEntity();
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("[AiServerClient] embedding test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 重建知识库索引（更换 Embedding 模型或修复索引后需要）。
     */
    public boolean reindex(String kbName) {
        try {
            var resp = client().post()
                    .uri("/api/v1/knowledge/{kbName}/reindex", kbName)
                    .retrieve()
                    .toBodilessEntity();
            return resp.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("[AiServerClient] reindex failed for {}: {}", kbName, e.getMessage());
            return false;
        }
    }

    // ==================== 知识库管理 ====================

    /** 列出知识库 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listKnowledgeBases() {
        try {
            return client().get()
                    .uri("/api/v1/knowledge/list")
                    .retrieve()
                    .body(List.class);
        } catch (Exception e) {
            log.warn("[AiServerClient] listKnowledgeBases 失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 创建知识库（multipart/form-data） */
    @SuppressWarnings("unchecked")
    public Map<?, ?> createKnowledgeBase(String name) {
        log.info("[AiServerClient] createKnowledgeBase: {}", name);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("name", name);
        // 必须提供至少一个文件，使用虚拟文件
        ByteArrayResource dummyFile = new ByteArrayResource("placeholder".getBytes()) {
            @Override
            public String getFilename() {
                return "placeholder.txt";
            }
        };
        body.add("files", dummyFile);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        String baseUrl = runtimeConfig.getAiServerBaseUrl();
        String url = baseUrl + "/api/v1/knowledge/create";

        // 使用带超时的 RestTemplate
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        RestTemplate restTemplate = new RestTemplate(factory);
        try {
            return restTemplate.postForObject(url, request, Map.class);
        } catch (Exception e) {
            log.warn("[AiServerClient] createKnowledgeBase failed (may already exist): {}", e.getMessage());
            return null;
        }
    }

    /** 上传文档到知识库（multipart/form-data），read 超时 120s */
    @SuppressWarnings("unchecked")
    public Map<?, ?> uploadDocument(String kbName, String fileName, byte[] content, String mimeType) {
        log.info("[AiServerClient] uploadDocument: kb={}, file={}, size={}", kbName, fileName, content.length);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        body.add("files", fileResource);

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        String baseUrl = runtimeConfig.getAiServerBaseUrl();
        String url = baseUrl + "/api/v1/knowledge/" + kbName + "/upload";

        // 大文件上传需要更长超时
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000);
        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate.postForObject(url, request, Map.class);
    }

    /** 删除整个知识库 */
    public boolean deleteKnowledgeBase(String kbName) {
        try {
            client().delete()
                    .uri("/api/v1/knowledge/{kbName}", kbName)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("[AiServerClient] delete KB failed: {}", e.getMessage());
            return false;
        }
    }

    /** 查看知识库中的文件列表 */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getKnowledgeBaseFiles(String kbName) {
        try {
            return client().get()
                    .uri("/api/v1/knowledge/{kbName}/files", kbName)
                    .retrieve()
                    .body(List.class);
        } catch (Exception e) {
            log.warn("[AiServerClient] list KB files failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** 查看知识库向量化处理进度 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getProgress(String kbName) {
        try {
            return client().get()
                    .uri("/api/v1/knowledge/{kbName}/progress", kbName)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("[AiServerClient] get progress failed: {}", e.getMessage());
            return Map.of();
        }
    }

    // ==================== RAG 检索 ====================

    /**
     * 在知识库中搜索最相关的文档片段（RAG 语义检索）。
     * 注意：ai-server 的 search 接口接受 Form 表单（multipart/form-data），不是 JSON。
     *
     * @param kbName 知识库名称
     * @param query  查询文本
     * @param topK   返回结果数量
     * @return 检索结果列表，每个元素包含 text、score、metadata 等
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> searchKnowledgeBase(String kbName, String query, int topK) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("query", query);
            body.add("top_k", String.valueOf(topK));

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            String baseUrl = runtimeConfig.getAiServerBaseUrl();
            String url = baseUrl + "/api/v1/knowledge/" + kbName + "/search";

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(5000);
            factory.setReadTimeout(30000);
            RestTemplate restTemplate = new RestTemplate(factory);
            Map<String, Object> result = restTemplate.postForObject(url, request, Map.class);

            if (result != null && result.containsKey("sources")) {
                return (List<Map<String, Object>>) result.get("sources");
            }
            return List.of();
        } catch (Exception e) {
            log.warn("[AiServerClient] KB search failed: {}", e.getMessage());
            return List.of();
        }
    }

    // ==================== 画像记忆 ====================

    /** 获取画像记忆（DeepTutor Persistent Memory） */
    @SuppressWarnings("unchecked")
    public Map<?, ?> getMemory(Long userId) {
        return client().get()
                .uri("/api/v1/memory")
                .retrieve()
                .body(Map.class);
    }

    /** 更新画像记忆 */
    @SuppressWarnings("unchecked")
    public Map<?, ?> updateMemory(Long userId, Map<String, Object> payload) {
        return client().post()
                .uri("/api/v1/memory")
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

}
