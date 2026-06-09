package com.lqragent.backend.agents.mediageneration.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import com.lqragent.backend.agents.content.summary.lessongeneration.entity.ResourceItem;
import com.lqragent.backend.agents.content.summary.lessongeneration.repository.ResourceItemRepository;
import com.lqragent.backend.agents.mediageneration.dto.MediaResult;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.service.KnowledgeGraphService;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 媒体生成服务。
 * 图片模式（由 image.binding / image.model / image.api-key / image.host 控制）：
 * - agnes：Agnes AI 图片生成（免费，OpenAI 兼容协议）
 * - siliconflow：SiliconFlow 可图 Kolors
 * - dalle3：OpenAI DALL·E 3
 * - sd3：Stability AI
 * - mock：占位符（默认）
 * 视频模式（由 video.binding / video.model / video.api-key / video.host 控制）：
 * - agnes：Agnes AI 视频生成（免费，异步任务模式）
 * - mock：占位符（默认）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaGenerationService {

    private final ResourceItemRepository resourceRepo;
    private final KnowledgeGraphService kgService;
    private final AppRuntimeConfig runtimeConfig;

    private static final String MEDIA_DIR = "media/generated";

    /**
     * 创建带超时配置的 RestClient。
     * 图片生成 connectTimeout=10s, readTimeout=60s。
     * 视频生成 connectTimeout=10s, readTimeout=120s。
     */
    private static RestClient createMediaRestClient(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().requestFactory(factory).build();
    }

    /** 图片 RestClient：10s 连接超时 + 60s 读取超时 */
    private static RestClient imageClient() {
        return createMediaRestClient(10_000, 60_000);
    }

    /** 视频 RestClient：15s 连接超时 + 300s 读取超时 */
    private static RestClient videoClient() {
        return createMediaRestClient(15_000, 300_000);
    }

    @Transactional
    public MediaResult generate(String kpId, String prompt) {
        KnowledgePoint kp = kgService.getByKpId(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + kpId));

        String provider = runtimeConfig.get(ConfigKeys.IMAGE_BINDING, "mock");
        String apiKey = runtimeConfig.get(ConfigKeys.IMAGE_API_KEY, "");
        String host = runtimeConfig.get(ConfigKeys.IMAGE_HOST, "https://apihub.agnes-ai.com/v1");
        String model = runtimeConfig.get(ConfigKeys.IMAGE_MODEL, "agnes-image-2.1-flash");
        String finalPrompt = prompt != null ? prompt : kp.getTitle() + " 概念示意图，教学用，清晰简洁";
        String imageUrl;
        String mime;

        switch (provider) {
            case "agnes" -> {
                imageUrl = callAgnesImage(finalPrompt, apiKey, host, model);
                mime = "image/png";
            }
            case "siliconflow" -> {
                imageUrl = callSiliconFlowKolors(finalPrompt, apiKey, host, model);
                mime = "image/png";
            }
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

    /**
     * 调用 Agnes AI 图片生成（OpenAI 兼容协议）。
     * POST /v1/images/generations
     */
    private String callAgnesImage(String prompt, String apiKey, String host, String model) {
        if (apiKey.isBlank()) {
            log.warn("[MediaGeneration] Agnes Image API key not configured, using mock");
            return buildPlaceholderSvg(prompt);
        }
        try {
            String base = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
            String response = imageClient()
                    .post()
                    .uri(base + "/images/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "prompt", prompt,
                            "size", "1024x1024"
                    ))
                    .retrieve()
                    .body(String.class);

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(response);
            var data = root.path("data");
            if (data.isArray() && data.size() > 0) {
                String imageUrl = data.get(0).path("url").asText("");
                if (!imageUrl.isBlank()) {
                    log.info("[MediaGeneration] Agnes Image 生成成功: {}", imageUrl);
                    return imageUrl;
                }
            }
            log.warn("[MediaGeneration] Agnes Image 响应解析失败: {}", response);
            return buildPlaceholderSvg(prompt);
        } catch (Exception e) {
            log.error("[MediaGeneration] Agnes Image 调用失败: {}", e.getMessage());
            return buildPlaceholderSvg(prompt);
        }
    }

    /**
     * 调用 SiliconFlow 可图 Kolors API 生成图片
     */
    private String callSiliconFlowKolors(String prompt, String apiKey, String host, String model) {
        if (apiKey.isBlank()) {
            log.warn("[MediaGeneration] SiliconFlow API key not configured, using mock");
            return buildPlaceholderSvg(prompt);
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "prompt", prompt,
                    "image_size", "1024x1024",
                    "num_inference_steps", 20
            );
            String base = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
            String response = imageClient()
                    .post()
                    .uri(base + "/images/generations")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(response);
            var images = root.path("images");
            if (images.isArray() && images.size() > 0) {
                String imageUrl = images.get(0).path("url").asText("");
                if (!imageUrl.isBlank()) {
                    log.info("[MediaGeneration] SiliconFlow Kolors 生成成功: {}", imageUrl);
                    return imageUrl;
                }
            }
            log.warn("[MediaGeneration] SiliconFlow 响应解析失败, using mock");
            return buildPlaceholderSvg(prompt);
        } catch (Exception e) {
            log.error("[MediaGeneration] SiliconFlow 调用失败: {}", e.getMessage());
            return buildPlaceholderSvg(prompt);
        }
    }

    private String callDalle3(String prompt) {
        String apiKey = runtimeConfig.get(ConfigKeys.LLM_API_KEY, "");
        if (apiKey.isBlank()) {
            log.warn("[MediaGeneration] DALL·E API Key 未配置");
            return buildPlaceholderSvg("API Key 未配置");
        }
        try {
            RestClient client = imageClient();
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
        String apiKey = runtimeConfig.get(ConfigKeys.IMAGE_API_KEY, "");
        if (apiKey.isBlank()) {
            log.warn("[MediaGeneration] Stability AI API Key 未配置");
            return buildPlaceholderSvg("API Key 未配置");
        }
        try {
            RestClient client = imageClient();
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
     * 生成教学视频。
     * 视频 provider 由 video.binding 控制。
     * agnes 模式：调用 Agnes AI 视频生成 API（异步任务模式）。
     */
    @Transactional
    public MediaResult generateVideo(String kpId, String prompt) {
        KnowledgePoint kp = kgService.getByKpId(kpId)
                .orElseThrow(() -> new IllegalArgumentException("知识点不存在: " + kpId));

        String provider = runtimeConfig.get(ConfigKeys.VIDEO_BINDING, "mock");
        String apiKey = runtimeConfig.get(ConfigKeys.VIDEO_API_KEY, "");
        String host = runtimeConfig.get(ConfigKeys.VIDEO_HOST, "https://apihub.agnes-ai.com/v1");
        String model = runtimeConfig.get(ConfigKeys.VIDEO_MODEL, "agnes-video-v2.0");
        String finalPrompt = prompt != null ? prompt : kp.getTitle() + " 教学演示视频";
        String videoUrl;

        switch (provider) {
            case "agnes" -> {
                videoUrl = callAgnesVideo(finalPrompt, apiKey, host, model, 5);
            }
            default -> {
                videoUrl = "";
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

    /**
     * 调用 Agnes AI 视频生成（异步任务模式）。
     * POST /v1/videos → 提交任务
     * GET  /v1/videos/{taskId} → 轮询结果
     * 官方文档：https://agnes-ai.com/doc
     * @param durationSeconds 目标时长（秒），5/10/18，默认 5
     */
    private String callAgnesVideo(String prompt, String apiKey, String host, String model, int durationSeconds) {
        if (apiKey.isBlank()) {
            log.warn("[MediaGeneration] Agnes Video API key not configured");
            return "";
        }
        try {
            String base = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
            RestClient client = videoClient();

            // 根据目标时长计算 num_frames（需满足 8n+1，上限 441，固定 frame_rate=24）
            // 官方文档推荐值：5s→121, 10s→241, 18s→441
            int numFrames;
            switch (durationSeconds) {
                case 18 -> numFrames = 441;
                case 10 -> numFrames = 241;
                default -> numFrames = 121; // 5s default
            }

            log.info("[MediaGeneration] Agnes Video 时长={}s, num_frames={}, frame_rate=24, model={}", durationSeconds, numFrames, model);

            // Step 1: 提交生成任务（官方文档：POST /v1/videos）
            String submitResp = client.post()
                    .uri(base + "/videos")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", model,
                            "prompt", prompt,
                            "height", 768,
                            "width", 1152,
                            "num_frames", numFrames,
                            "frame_rate", 24
                    ))
                    .retrieve()
                    .body(String.class);

            log.debug("[MediaGeneration] Agnes Video 提交响应: {}", submitResp);

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(submitResp);
            // 官方文档：提交成功返回 { "id": "task_id" }
            String taskId = root.path("id").asText("");
            if (taskId.isBlank()) {
                // 尝试 task_id 作为备选字段名
                taskId = root.path("task_id").asText("");
            }
            if (taskId.isBlank()) {
                // 检查是否有错误信息
                String error = root.path("error").path("message").asText("");
                if (!error.isBlank()) {
                    log.error("[MediaGeneration] Agnes Video 提交失败: error={}, 完整响应: {}", error, submitResp);
                } else {
                    log.warn("[MediaGeneration] Agnes Video 任务提交响应中无 id 字段: {}", submitResp);
                }
                return "";
            }
            log.info("[MediaGeneration] Agnes Video 任务已提交: taskId={}", taskId);

            // Step 2: 轮询等待结果（官方文档：GET /v1/videos/{taskId}，最多 15 分钟）
            // 推荐轮询间隔 5 秒，最大尝试 180 次
            int maxAttempts = 180;
            int intervalSec = 5;
            for (int i = 0; i < maxAttempts; i++) {
                Thread.sleep(intervalSec * 1000L);
                String pollResp;
                try {
                    pollResp = client.get()
                            .uri(base + "/videos/" + taskId)
                            .header("Authorization", "Bearer " + apiKey)
                            .retrieve()
                            .body(String.class);
                } catch (Exception pollEx) {
                    log.warn("[MediaGeneration] Agnes Video 轮询请求失败 (attempt={}): {}", i + 1, pollEx.getMessage());
                    continue; // 网络抖动时跳过，继续下一次轮询
                }

                var pollRoot = mapper.readTree(pollResp);
                String status = pollRoot.path("status").asText("pending");

                // 每 10 次轮询打印完整响应用于诊断排队情况
                if (i == 0 || (i + 1) % 10 == 0) {
                    log.info("[MediaGeneration] Agnes Video 轮询 #{}/{}: status={}, 完整响应: {}", i + 1, maxAttempts, status, pollResp);
                }
                // 排队超过 2 分钟仍未开始处理，打印警告
                if (i >= 24 && "queued".equalsIgnoreCase(status)) {
                    log.warn("[MediaGeneration] Agnes Video 已排队 {} 秒仍未开始处理，当前队列可能较长，请稍后重试", (i + 1) * intervalSec);
                }

                // 官方文档：completed 表示生成成功，返回 video_url
                if ("completed".equalsIgnoreCase(status) || "succeeded".equalsIgnoreCase(status)) {
                    // 按优先级提取视频 URL：video_url > output.video_url > remixed_from_video_id > url
                    String videoUrl = pollRoot.path("video_url").asText("");
                    if (videoUrl.isBlank()) {
                        videoUrl = pollRoot.path("output").path("video_url").asText("");
                    }
                    if (videoUrl.isBlank()) {
                        videoUrl = pollRoot.path("remixed_from_video_id").asText("");
                    }
                    if (videoUrl.isBlank()) {
                        videoUrl = pollRoot.path("url").asText("");
                    }
                    if (videoUrl.isBlank()) {
                        log.warn("[MediaGeneration] Agnes Video 状态已完成但未找到 video_url, 完整响应: {}", pollResp);
                        return "";
                    }
                    log.info("[MediaGeneration] Agnes Video 生成完成: url={}", videoUrl);
                    return videoUrl;
                }
                if ("failed".equalsIgnoreCase(status) || "error".equalsIgnoreCase(status)) {
                    log.error("[MediaGeneration] Agnes Video 生成失败, 完整响应: {}", pollResp);
                    return "";
                }
                if (!"pending".equalsIgnoreCase(status) && !"processing".equalsIgnoreCase(status)
                        && !"queued".equalsIgnoreCase(status) && !"running".equalsIgnoreCase(status)) {
                    log.info("[MediaGeneration] Agnes Video 未知状态: status={}, 完整响应: {}", status, pollResp);
                }
            }
            log.warn("[MediaGeneration] Agnes Video 轮询超时（{}秒）", maxAttempts * intervalSec);
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[MediaGeneration] Agnes Video 轮询被中断");
            return "";
        } catch (Exception e) {
            log.error("[MediaGeneration] Agnes Video 调用失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 自由生图：直接用 prompt 生成图片，不需要知识点 ID（控制台测试用）。
     */
    public String generateImageByPrompt(String prompt) {
        String provider = runtimeConfig.get(ConfigKeys.IMAGE_BINDING, "mock");
        String apiKey = runtimeConfig.get(ConfigKeys.IMAGE_API_KEY, "");
        String host = runtimeConfig.get(ConfigKeys.IMAGE_HOST, "https://apihub.agnes-ai.com/v1");
        String model = runtimeConfig.get(ConfigKeys.IMAGE_MODEL, "agnes-image-2.1-flash");

        switch (provider) {
            case "agnes" -> {
                return callAgnesImage(prompt, apiKey, host, model);
            }
            case "siliconflow" -> {
                return callSiliconFlowKolors(prompt, apiKey, host, model);
            }
            case "dalle3" -> {
                return callDalle3(prompt);
            }
            case "sd3" -> {
                return callStableDiffusion(prompt);
            }
            default -> {
                return buildPlaceholderSvg("示意图");
            }
        }
    }

    /**
     * 自由生视频：直接用 prompt 生成视频，不需要知识点 ID（控制台测试用）。
     * @param durationSeconds 目标时长（秒），5/10/18，默认 5
     */
    public String generateVideoByPrompt(String prompt, int durationSeconds) {
        String provider = runtimeConfig.get(ConfigKeys.VIDEO_BINDING, "mock");
        String apiKey = runtimeConfig.get(ConfigKeys.VIDEO_API_KEY, "");
        String host = runtimeConfig.get(ConfigKeys.VIDEO_HOST, "https://apihub.agnes-ai.com/v1");
        String model = runtimeConfig.get(ConfigKeys.VIDEO_MODEL, "agnes-video-v2.0");

        switch (provider) {
            case "agnes" -> {
                return callAgnesVideo(prompt, apiKey, host, model, durationSeconds);
            }
            default -> {
                return "";
            }
        }
    }

    /** 兼容旧接口：默认 5 秒 */
    public String generateVideoByPrompt(String prompt) {
        return generateVideoByPrompt(prompt, 5);
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
