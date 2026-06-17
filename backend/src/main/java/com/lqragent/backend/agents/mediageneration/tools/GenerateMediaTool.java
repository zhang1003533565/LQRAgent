package com.lqragent.backend.agents.mediageneration.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.mediageneration.service.MediaGenerationService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class GenerateMediaTool implements AgentTool {

    private final MediaGenerationService mediaService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String name() { return "generate_media"; }

    @Override
    public String description() { return "生成媒体内容：图片、视频等"; }

    @Override
    public Map<String, Object> parameterSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "kpId", Map.of("type", "string", "description", "知识点ID"),
                        "prompt", Map.of("type", "string", "description", "生成提示词"),
                        "mediaType", Map.of("type", "string", "description", "媒体类型：image/video")
                ),
                "required", new String[]{"prompt"}
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> args) {
        try {
            String prompt = args.get("prompt") != null ? args.get("prompt").toString() : "";
            String kpId = args.get("kpId") != null ? args.get("kpId").toString() : "kp_default";
            String mediaType = args.get("mediaType") != null ? args.get("mediaType").toString() : "image";

            String finalPrompt = "video".equalsIgnoreCase(mediaType) ? prompt : normalizeImagePrompt(prompt);

            String mediaUrl;
            if ("video".equalsIgnoreCase(mediaType)) {
                mediaUrl = mediaService.generateVideoByPrompt(finalPrompt);
            } else {
                mediaUrl = mediaService.generateImageByPrompt(finalPrompt);
            }

            Map<String, Object> data = Map.of(
                    "mediaType", mediaType,
                    "prompt", finalPrompt,
                    "mediaUrl", mediaUrl != null ? mediaUrl : ""
            );
            return ToolResult.success(mapper.writeValueAsString(data));
        } catch (Exception e) {
            return ToolResult.failure("媒体生成失败: " + e.getMessage());
        }
    }

    private String normalizeImagePrompt(String prompt) {
        String base = prompt == null || prompt.isBlank() ? "A clean educational illustration" : prompt.trim();
        return base + ", single educational illustration, one coherent scene, centered composition, clean modern style, clear visual hierarchy, no multi-panel layout, no grid, no collage, no comic panels, no storyboard, no split screen, no nine-grid, no multiple frames, no dense text, no small unreadable labels";
    }
}
