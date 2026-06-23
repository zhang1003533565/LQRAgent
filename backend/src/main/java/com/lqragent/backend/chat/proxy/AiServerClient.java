package com.lqragent.backend.chat.proxy;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    public boolean ping() {
        try {
            client().get().uri("/api/v1/knowledge/health").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("[AiServerClient] ping failed: {}", e.getMessage());
            return false;
        }
    }

    public List<Map<String, Object>> listKnowledgeBases() {
        Object body = client().get()
                .uri("/api/v1/knowledge/list")
                .retrieve()
                .body(Object.class);
        if (body instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) list;
            return result;
        }
        if (body instanceof Map<?, ?> map) {
            Object list = map.get("knowledge_bases");
            if (list instanceof List<?> kbList) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = (List<Map<String, Object>>) kbList;
                return result;
            }
        }
        return List.of();
    }

    public Map<String, Object> createKnowledgeBase(String name, String fileName, byte[] content, String mimeType) {
        log.info("[AiServerClient] createKnowledgeBase: name={}, file={}, size={}", name, fileName, content.length);
        MultiValueMap<String, Object> body = buildMultipartBody(fileName, content, mimeType);
        body.add("name", name);
        return multipartPost(runtimeConfig.getAiServerBaseUrl() + "/api/v1/knowledge/create", body);
    }

    public Map<String, Object> uploadDocument(String kbName, String fileName, byte[] content, String mimeType) {
        log.info("[AiServerClient] uploadDocument: kb={}, file={}, size={}", kbName, fileName, content.length);
        String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
        return multipartPost(
                runtimeConfig.getAiServerBaseUrl() + "/api/v1/knowledge/" + encodedKbName + "/upload",
                buildMultipartBody(fileName, content, mimeType)
        );
    }

    public Map<String, Object> getProgress(String kbName) {
        String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
        return client().get()
                .uri("/api/v1/knowledge/" + encodedKbName + "/progress")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
    }

    public boolean knowledgeBaseExists(String kbName) {
        try {
            String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
            client().get()
                    .uri("/api/v1/knowledge/" + encodedKbName)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 知识库向量检索（Chroma / 本地索引均由 ai-server 路由）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> searchKnowledgeBase(String kbName, String query, int topK) {
        String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", query);
        form.add("top_k", String.valueOf(topK));
        try {
            return client().post()
                    .uri("/api/v1/knowledge/" + encodedKbName + "/search")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("[AiServerClient] searchKnowledgeBase failed: kb={}, error={}", kbName, e.getMessage());
            return Map.of("needs_reindex", true, "error", e.getMessage());
        }
    }

    /**
     * 触发知识库向量重建（写入 Docker Chroma）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> reindexKnowledgeBase(String kbName) {
        String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
        log.info("[AiServerClient] reindexKnowledgeBase: {}", kbName);
        return client().post()
                .uri("/api/v1/knowledge/" + encodedKbName + "/reindex")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
    
    /**
     * 删除知识库
     */
    public boolean deleteKnowledgeBase(String kbName) {
        log.info("[AiServerClient] deleteKnowledgeBase: name={}", kbName);
        try {
            String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
            client().delete()
                    .uri("/api/v1/knowledge/" + encodedKbName)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("[AiServerClient] deleteKnowledgeBase failed: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 列出知识库中的文档
     */
    public List<Map<String, Object>> listDocuments(String kbName) {
        String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
        Object body = client().get()
                .uri("/api/v1/knowledge/" + encodedKbName + "/documents")
                .retrieve()
                .body(Object.class);
        if (body instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> result = (List<Map<String, Object>>) list;
            return result;
        }
        if (body instanceof Map<?, ?> map) {
            Object list = map.get("documents");
            if (list instanceof List<?> docList) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = (List<Map<String, Object>>) docList;
                return result;
            }
        }
        return List.of();
    }
    
    /**
     * 获取文档的切分块内容
     */
    public List<Map<String, Object>> getDocumentChunks(String kbName, String fileName) {
        String encodedKbName = UriUtils.encodePathSegment(kbName, StandardCharsets.UTF_8);
        String encodedFileName = UriUtils.encodePathSegment(fileName, StandardCharsets.UTF_8);
        String url = runtimeConfig.getAiServerBaseUrl()
                + "/api/v1/knowledge/" + encodedKbName + "/document/" + encodedFileName + "/chunks";
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
        restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
        String json = restTemplate.getForObject(url, String.class);
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            Object body = new ObjectMapper().readValue(json, Object.class);
            if (body instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = (List<Map<String, Object>>) list;
                return result;
            }
            if (body instanceof Map<?, ?> map) {
                Object list = map.get("chunks");
                if (list instanceof List<?> chunkList) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> result = (List<Map<String, Object>>) chunkList;
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("[AiServerClient] parse chunks response failed: {}", e.getMessage());
        }
        return List.of();
    }

    public Map<?, ?> getMemory(Long userId) {
        return client().get()
                .uri("/api/v1/memory")
                .retrieve()
                .body(Map.class);
    }

    public Map<?, ?> updateMemory(Long userId, Map<String, Object> payload) {
        return client().post()
                .uri("/api/v1/memory")
                .body(payload)
                .retrieve()
                .body(Map.class);
    }

    private MultiValueMap<String, Object> buildMultipartBody(String fileName, byte[] content, String mimeType) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource fileResource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(
                mimeType != null && !mimeType.isBlank() ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE
        ));
        body.add("files", new HttpEntity<>(fileResource, fileHeaders));
        return body;
    }

    private Map<String, Object> multipartPost(String url, MultiValueMap<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
        return response;
    }
}
