package com.lqragent.backend.admin.service;

import com.lqragent.backend.admin.dto.ModelConfigDto;
import com.lqragent.backend.admin.dto.ModelConfigSaveRequest;
import com.lqragent.backend.aiserver.AiServerEnvSyncService;
import com.lqragent.backend.common.exception.BusinessException;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.lqragent.backend.systemconfig.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private static final String MASK = "********";

    private final SysConfigService sysConfigService;
    private final AppRuntimeConfig runtimeConfig;
    private final AiServerEnvSyncService envSyncService;

    public ModelConfigDto getModelConfig() {
        String llmKey = runtimeConfig.get(ConfigKeys.LLM_API_KEY);
        String embKey = runtimeConfig.get(ConfigKeys.EMBEDDING_API_KEY);
        return ModelConfigDto.builder()
                .llmBinding(runtimeConfig.get(ConfigKeys.LLM_BINDING, "openai"))
                .llmModel(runtimeConfig.get(ConfigKeys.LLM_MODEL, "gpt-4o-mini"))
                .llmApiKeyMasked(mask(llmKey))
                .llmApiKeySet(llmKey != null && !llmKey.isBlank())
                .llmHost(runtimeConfig.get(ConfigKeys.LLM_HOST, "https://api.openai.com/v1"))
                .llmApiVersion(runtimeConfig.get(ConfigKeys.LLM_API_VERSION))
                .embeddingBinding(runtimeConfig.get(ConfigKeys.EMBEDDING_BINDING, "openai"))
                .embeddingModel(runtimeConfig.get(ConfigKeys.EMBEDDING_MODEL, "text-embedding-3-large"))
                .embeddingApiKeyMasked(mask(embKey))
                .embeddingApiKeySet(embKey != null && !embKey.isBlank())
                .embeddingHost(runtimeConfig.get(ConfigKeys.EMBEDDING_HOST,
                        "https://api.openai.com/v1/embeddings"))
                .build();
    }

    public ModelConfigDto saveModelConfig(ModelConfigSaveRequest req) {
        upsert(ConfigKeys.LLM_BINDING, req.getLlmBinding(), "大模型提供商 binding");
        upsert(ConfigKeys.LLM_MODEL, req.getLlmModel(), "大模型名称");
        upsertIfNotBlank(ConfigKeys.LLM_API_KEY, req.getLlmApiKey(), "大模型 API Key");
        upsert(ConfigKeys.LLM_HOST, req.getLlmHost(), "大模型 API 地址 (LLM_HOST)");
        upsert(ConfigKeys.LLM_API_VERSION, req.getLlmApiVersion(), "大模型 API 版本（Azure 等）");

        upsert(ConfigKeys.EMBEDDING_BINDING, req.getEmbeddingBinding(), "嵌入模型提供商");
        upsert(ConfigKeys.EMBEDDING_MODEL, req.getEmbeddingModel(), "嵌入模型名称");
        upsertIfNotBlank(ConfigKeys.EMBEDDING_API_KEY, req.getEmbeddingApiKey(), "嵌入 API Key");
        upsert(ConfigKeys.EMBEDDING_HOST, req.getEmbeddingHost(), "嵌入 API 完整地址");

        if (req.isSyncToAiServer()) {
            try {
                envSyncService.syncModelConfigToEnv();
            } catch (Exception e) {
                log.error("[ModelConfig] 同步 ai-server/.env 失败", e);
                throw BusinessException.of("配置已保存，但同步 ai-server/.env 失败：" + e.getMessage());
            }
        }

        return getModelConfig();
    }

    public Map<String, Object> testLlmConnection() {
        String host = runtimeConfig.get(ConfigKeys.LLM_HOST);
        String apiKey = runtimeConfig.get(ConfigKeys.LLM_API_KEY);
        String model = runtimeConfig.get(ConfigKeys.LLM_MODEL, "gpt-4o-mini");

        if (host == null || host.isBlank()) {
            throw BusinessException.of("请先配置大模型 API 地址 (llm.host)");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw BusinessException.of("请先配置大模型 API Key (llm.api-key)");
        }

        String base = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
        String url = base + "/chat/completions";

        try {
            RestClient client = RestClient.builder().build();
            @SuppressWarnings("unchecked")
            Map<String, Object> body = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .body(Map.of(
                            "model", model,
                            "messages", List.of(Map.of("role", "user", "content", "ping")),
                            "max_tokens", 8
                    ))
                    .retrieve()
                    .body(Map.class);

            return Map.of(
                    "success", true,
                    "message", "大模型 API 连接成功",
                    "endpoint", url,
                    "model", model
            );
        } catch (Exception e) {
            log.warn("[ModelConfig] LLM test failed: {}", e.getMessage());
            return Map.of(
                    "success", false,
                    "message", "连接失败：" + e.getMessage(),
                    "endpoint", url,
                    "model", model
            );
        }
    }

    private void upsert(String key, String value, String remark) {
        if (value == null) {
            return;
        }
        sysConfigService.upsert(key, value.trim(), remark);
    }

    private void upsertIfNotBlank(String key, String value, String remark) {
        if (value != null && !value.isBlank() && !MASK.equals(value)) {
            sysConfigService.upsert(key, value.trim(), remark);
        }
    }

    private static String mask(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        return MASK;
    }
}
