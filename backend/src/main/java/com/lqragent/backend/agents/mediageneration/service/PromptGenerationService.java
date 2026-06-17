package com.lqragent.backend.agents.mediageneration.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 提示词生成服务（提示词智能体）。
 * 根据用户意图，通过 LLM 生成适合图片/视频的提示词。
 * 同时判断应该生成图片还是视频。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptGenerationService {

    private final LlmClient llmClient;
    private final AppRuntimeConfig runtimeConfig;

    private static final String SYSTEM_PROMPT = """
            你是一个专业的媒体内容提示词生成器。根据用户的意图，生成适合 AI 图片生成或视频生成的英文提示词。

            你需要：
            1. 理解用户想要什么内容
            2. 生成一个高质量的英文提示词（AI 图片/视频模型通常对英文提示词效果更好）
            3. 判断这个内容适合生成图片还是视频

            返回格式必须是严格的 JSON：
            {"prompt": "生成的英文提示词", "mediaType": "image 或 video", "reason": "选择该媒体类型的简短原因"}

            注意事项：
            - 提示词应该是英文，详细描述画面内容、风格、构图
            - 图片必须是单张完整画面：single coherent scene, centered composition
            - 图片提示词必须明确禁止九宫格/多面板/拼贴/分镜：no grid, no multi-panel layout, no collage, no storyboard, no split screen, no nine-grid
            - 不要包含任何教学相关的后缀（如 concept diagram, teaching illustration 等），除非用户明确要求
            - 如果用户说"生成图片"或描述静态场景，mediaType 用 "image"
            - 如果用户说"生成视频"或描述动态过程/动画，mediaType 用 "video"
            - 默认情况下，简单场景用 image，动态演示用 video
            - 只输出 JSON，不要其他文字
            """;

    /**
     * 根据用户意图生成提示词。
     *
     * @param intent    用户意图描述
     * @param mediaType 媒体类型提示（image/video/auto）
     * @return 包含 prompt、mediaType、reason 的 Map
     */
    public Map<String, Object> generatePrompt(String intent, String mediaType) {
        if (intent == null || intent.isBlank()) {
            return Map.of(
                    "prompt", "A clean, modern illustration",
                    "mediaType", "image",
                    "reason", "默认使用图片",
                    "success", true
            );
        }

        String apiKey = runtimeConfig.get(ConfigKeys.LLM_API_KEY, "");
        String host = runtimeConfig.get(ConfigKeys.LLM_HOST, "");
        if (apiKey.isBlank() || host.isBlank()) {
            log.warn("[PromptGeneration] LLM 未配置，直接返回原始意图作为提示词");
            return Map.of(
                    "prompt", intent,
                    "mediaType", "image",
                    "reason", "LLM 未配置，使用原始输入",
                    "success", true
            );
        }

        String userMessage = "用户意图：" + intent + "\n期望媒体类型：" + mediaType;

        try {
            String response = llmClient.chatSimple(SYSTEM_PROMPT, userMessage);
            if (response == null || response.isBlank()) {
                log.warn("[PromptGeneration] LLM 返回空响应");
                return fallback(intent, mediaType);
            }

            // 尝试解析 JSON 响应
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String cleaned = response.trim();
            // 去掉可能的 markdown 代码块包裹
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("^```(?:json)?\\s*", "").replaceAll("```\\s*$", "");
            }

            var root = mapper.readTree(cleaned);
            String prompt = root.path("prompt").asText(intent);
            String detectedType = root.path("mediaType").asText("image");
            String reason = root.path("reason").asText("LLM 生成");

            // 如果用户指定了 mediaType 且不是 auto，优先使用用户的指定
            if ("image".equals(mediaType) || "video".equals(mediaType)) {
                detectedType = mediaType;
            }

            log.info("[PromptGeneration] 生成完成: mediaType={}, reason={}", detectedType, reason);
            return Map.of(
                    "prompt", prompt,
                    "mediaType", detectedType,
                    "reason", reason,
                    "success", true
            );
        } catch (Exception e) {
            log.error("[PromptGeneration] LLM 调用失败: {}", e.getMessage());
            return fallback(intent, mediaType);
        }
    }

    private Map<String, Object> fallback(String intent, String mediaType) {
        String type = ("video".equals(mediaType)) ? "video" : "image";
        return Map.of(
                "prompt", intent,
                "mediaType", type,
                "reason", "LLM 调用失败，使用原始输入",
                "success", true
        );
    }
}
