package com.lqragent.backend.admin.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.lqragent.backend.admin.dto.ModelConfigDto;
import com.lqragent.backend.admin.dto.ModelConfigSaveRequest;
import com.lqragent.backend.chat.proxy.AiServerEnvSyncService;
import com.lqragent.backend.common.exception.BusinessException;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.lqragent.backend.systemconfig.service.SysConfigService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
        String videoKey = runtimeConfig.get(ConfigKeys.VIDEO_API_KEY);
        String imageKey = runtimeConfig.get(ConfigKeys.IMAGE_API_KEY);
        String ocrKey = runtimeConfig.get(ConfigKeys.OCR_API_KEY);
        String ocrSecret = runtimeConfig.get(ConfigKeys.OCR_SECRET_KEY);
        return ModelConfigDto.builder()
                .llmBinding(runtimeConfig.get(ConfigKeys.LLM_BINDING, "openai"))
                .llmModel(runtimeConfig.get(ConfigKeys.LLM_MODEL, "gpt-4o-mini"))
                .llmApiKeyMasked(mask(llmKey))
                .llmApiKeySet(llmKey != null && !llmKey.isBlank())
                .llmHost(runtimeConfig.get(ConfigKeys.LLM_HOST, "https://api.openai.com/v1"))
                .llmApiVersion(runtimeConfig.get(ConfigKeys.LLM_API_VERSION))
                .embeddingBinding(runtimeConfig.get(ConfigKeys.EMBEDDING_BINDING, "xfyun"))
                .embeddingModel(runtimeConfig.get(ConfigKeys.EMBEDDING_MODEL, "xop3qwen8bembedding"))
                .embeddingApiKeyMasked(mask(embKey))
                .embeddingApiKeySet(embKey != null && !embKey.isBlank())
                .embeddingHost(runtimeConfig.get(ConfigKeys.EMBEDDING_HOST,
                        "https://maas-api.cn-huabei-1.xf-yun.com/v2/embeddings"))
                .videoBinding(runtimeConfig.get(ConfigKeys.VIDEO_BINDING, "agnes"))
                .videoModel(runtimeConfig.get(ConfigKeys.VIDEO_MODEL, "agnes-video-v2.0"))
                .videoApiKeyMasked(mask(videoKey))
                .videoApiKeySet(videoKey != null && !videoKey.isBlank())
                .videoHost(runtimeConfig.get(ConfigKeys.VIDEO_HOST, "https://apihub.agnes-ai.com/v1"))
                .imageBinding(runtimeConfig.get(ConfigKeys.IMAGE_BINDING, "agnes"))
                .imageModel(runtimeConfig.get(ConfigKeys.IMAGE_MODEL, "agnes-image-2.1-flash"))
                .imageApiKeyMasked(mask(imageKey))
                .imageApiKeySet(imageKey != null && !imageKey.isBlank())
                .imageHost(runtimeConfig.get(ConfigKeys.IMAGE_HOST, "https://apihub.agnes-ai.com/v1"))
                .ocrBinding(runtimeConfig.get(ConfigKeys.OCR_BINDING, "xfyun"))
                .ocrModel(runtimeConfig.get(ConfigKeys.OCR_MODEL, "xppaddleocrv16"))
                .ocrApiKeyMasked(mask(ocrKey))
                .ocrApiKeySet(ocrKey != null && !ocrKey.isBlank())
                .ocrSecretKeyMasked(mask(ocrSecret))
                .ocrSecretKeySet(ocrSecret != null && !ocrSecret.isBlank())
                .ocrHost(runtimeConfig.get(ConfigKeys.OCR_HOST, "https://maas-api.cn-huabei-1.xf-yun.com/v2"))
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

        upsert(ConfigKeys.VIDEO_BINDING, req.getVideoBinding(), "视频生成提供商");
        upsert(ConfigKeys.VIDEO_MODEL, req.getVideoModel(), "视频生成模型");
        upsertIfNotBlank(ConfigKeys.VIDEO_API_KEY, req.getVideoApiKey(), "视频生成 API Key");
        upsert(ConfigKeys.VIDEO_HOST, req.getVideoHost(), "视频生成 API 地址");

        upsert(ConfigKeys.IMAGE_BINDING, req.getImageBinding(), "图片生成提供商");
        upsert(ConfigKeys.IMAGE_MODEL, req.getImageModel(), "图片生成模型");
        upsertIfNotBlank(ConfigKeys.IMAGE_API_KEY, req.getImageApiKey(), "图片生成 API Key");
        upsert(ConfigKeys.IMAGE_HOST, req.getImageHost(), "图片生成 API 地址");

        upsert(ConfigKeys.OCR_BINDING, req.getOcrBinding(), "视觉识别/OCR 提供商");
        upsert(ConfigKeys.OCR_MODEL, req.getOcrModel(), "视觉识别/OCR 模型");
        upsertIfNotBlank(ConfigKeys.OCR_API_KEY, req.getOcrApiKey(), "OCR API Key");
        upsertIfNotBlank(ConfigKeys.OCR_SECRET_KEY, req.getOcrSecretKey(), "OCR Secret Key");
        upsert(ConfigKeys.OCR_HOST, req.getOcrHost(), "OCR API 地址");

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
