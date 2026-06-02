package com.lqragent.backend.agents.mediageneration.service;

import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.service.KnowledgeGraphService;
import com.lqragent.backend.agents.mediageneration.dto.MediaResult;
import com.lqragent.backend.agents.resourcegeneration.entity.ResourceItem;
import com.lqragent.backend.agents.resourcegeneration.repository.ResourceItemRepository;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 媒体生成服务。
 * 图片模式（由 sys_config agent.mediagen.image_provider 控制）：
 * - mock：占位符（默认）
 * - dalle3：调 OpenAI DALL·E 3 API
 * - sd3：调 Stability AI API
 * 视频模式（由 sys_config agent.mediagen.video_provider 控制）：
 * - mock：占位符（默认）
 * - seedance：预留，待接入字节 SeeDance API
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaGenerationService {

    private final ResourceItemRepository resourceRepo;
    private final KnowledgeGraphService kgService;
    private final AppRuntimeConfig runtimeConfig;

    private static final String MEDIA_DIR = "media/generated";

    @Transactional
    public MediaResult generate(String kpId, String prompt) {
        KnowledgePoint kp = kgService.getByKpId(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + kpId));

        String provider = runtimeConfig.get("agent.mediagen.image_provider", "mock");
        String finalPrompt = prompt != null ? prompt : kp.getTitle() + " 概念示意图，教学用，清晰简洁";
        String imageUrl;
        String mime;

        switch (provider) {
            case "dalle3" -> {
                imageUrl = callDalle3(finalPrompt);
                mime = "image/png";
            }
            case "sd3" -> {
                imageUrl = callStableDiffusion(finalPrompt);
                mime = "image/webp";
            }
            default -> {
                imageUrl = buildPlaceholderSvg("示意图");
                mime = "image/svg+xml";
            }
        }

        ResourceItem item = ResourceItem.builder()
                .kpId(kpId)
                .resourceType(ResourceItem.TYPE_ILLUSTRATION)
                .title(kp.getTitle() + " — 示意图")
                .content("<!-- AI 生成示意图 -->\n\n" + kp.getTitle() + " 概念示意图")
                .mediaUrl(imageUrl)
                .mediaMime(mime)
                .generationPrompt(finalPrompt)
                .build();
        item = resourceRepo.save(item);

        log.info("[MediaGeneration] 已生成: provider={}, resourceId={}, url={}", provider, item.getId(), imageUrl);
        return MediaResult.builder()
                .resourceId(item.getId()).kpId(kpId).mediaUrl(imageUrl)
                .mediaMime(mime).prompt(finalPrompt).newlyCreated(true).build();
    }

    /** 调 OpenAI DALL·E 3 */
    private String callDalle3(String prompt) {
        String apiKey = runtimeConfig.get("llm.api-key", "");
        if (apiKey.isBlank()) {
            log.warn("[MediaGeneration] DALL·E API Key 未配置");
            return buildPlaceholderSvg("API Key 未配置");
        }
        try {
            RestClient client = RestClient.builder().build();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .uri("https://api.openai.com/v1/images/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", "dall-e-3", "prompt", prompt, "n", 1, "size", "1024x1024"))
                    .retrieve()
                    .body(Map.class);
            if (resp != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> data = (List<Map<String, Object>>) resp.get("data");
                if (data != null && !data.isEmpty()) {
                    String url = (String) data.get(0).get("url");
                    if (url != null) return url;
                }
            }
        } catch (Exception e) {
            log.warn("[MediaGeneration] DALL·E 调用失败: {}", e.getMessage());
        }
        return buildPlaceholderSvg("DALL·E 生成失败");
    }

    /** 调 Stability AI */
    private String callStableDiffusion(String prompt) {
        String apiKey = runtimeConfig.get("agent.mediagen.api_key", "");
        if (apiKey.isBlank()) {
            log.warn("[MediaGeneration] Stability AI API Key 未配置");
            return buildPlaceholderSvg("API Key 未配置");
        }
        try {
            RestClient client = RestClient.builder().build();
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = client.post()
                    .uri("https://api.stability.ai/v2beta/stable-image/generate/ultra")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prompt", prompt, "output_format", "webp"))
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.containsKey("image")) {
                return "data:image/webp;base64," + resp.get("image");
            }
        } catch (Exception e) {
            log.warn("[MediaGeneration] Stability AI 调用失败: {}", e.getMessage());
        }
        return buildPlaceholderSvg("Stability AI 生成失败");
    }

    /**
     * 生成教学视频（预留接口）。
     * 视频 provider 由 sys_config agent.mediagen.video_provider 控制。
     * 当前仅支持 mock 模式，返回占位符。
     */
    @Transactional
    public MediaResult generateVideo(String kpId, String prompt) {
        KnowledgePoint kp = kgService.getByKpId(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + kpId));

        String provider = runtimeConfig.get("agent.mediagen.video_provider", "mock");
        String finalPrompt = prompt != null ? prompt : kp.getTitle() + " 教学演示视频";
        String videoUrl;

        switch (provider) {
            case "seedance" -> {
                // TODO: 接入字节 SeeDance API
                log.warn("[MediaGeneration] seedance 视频生成尚未实现，使用占位符");
                videoUrl = "";  // seedance 未实现，返回空
            }
            default -> {
                videoUrl = "";  // mock 模式，视频暂不生成
            }
        }

        ResourceItem item = ResourceItem.builder()
                .kpId(kpId)
                .resourceType(ResourceItem.TYPE_ILLUSTRATION)
                .title(kp.getTitle() + " — 教学视频")
                .content("<!-- AI 生成教学视频 -->\n\n" + kp.getTitle() + " 教学演示")
                .mediaUrl(videoUrl)
                .mediaMime("video/mp4")
                .generationPrompt(finalPrompt)
                .build();
        item = resourceRepo.save(item);

        log.info("[MediaGeneration] 视频生成: provider={}, resourceId={}, url={}", provider, item.getId(), videoUrl);
        return MediaResult.builder()
                .resourceId(item.getId()).kpId(kpId).mediaUrl(videoUrl)
                .mediaMime("video/mp4").prompt(finalPrompt).newlyCreated(true).build();
    }

    public Path getMediaPath(Long resourceId) {
        return resourceRepo.findById(resourceId)
                .map(item -> {
                    if (item.getMediaUrl() != null && !item.getMediaUrl().startsWith("PLACEHOLDER")) {
                        return Paths.get(MEDIA_DIR, resourceId + ".png");
                    }
                    return null;
                })
                .orElse(null);
    }

    /** 生成内联 SVG data URI 占位图，前端可直接 <img src> 渲染 */
    private String buildPlaceholderSvg(String label) {
        String svg = """
            <svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">
              <rect width="400" height="300" rx="16" fill="#eef2f7"/>
              <rect x="1" y="1" width="398" height="298" rx="15" fill="none" stroke="#c5d0de" stroke-width="1.5" stroke-dasharray="6 4"/>
              <circle cx="200" cy="120" r="36" fill="#c5d0de"/>
              <text x="200" y="128" text-anchor="middle" font-family="sans-serif" font-size="28" fill="#8b9ab6">?</text>
              <text x="200" y="190" text-anchor="middle" font-family="sans-serif" font-size="16" fill="#526989">%s</text>
              <text x="200" y="215" text-anchor="middle" font-family="sans-serif" font-size="12" fill="#8b9ab6">Mock 模式 · 配置 AI 图片 Provider 后自动生成</text>
            </svg>""".formatted(label);
        return "data:image/svg+xml," + java.net.URLEncoder.encode(svg, java.nio.charset.StandardCharsets.UTF_8);
    }
}
