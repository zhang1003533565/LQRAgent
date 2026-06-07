package com.lqragent.backend.agents.mediageneration.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lqragent.backend.agents.mediageneration.dto.MediaResult;
import com.lqragent.backend.agents.mediageneration.service.MediaGenerationService;
import com.lqragent.backend.agents.mediageneration.service.PromptGenerationService;
import com.lqragent.backend.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "媒体文件", description = "生成示意图、视频等媒体资源访问")
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaGenerationService mediaGenerationService;
    private final PromptGenerationService promptGenerationService;

    @Operation(summary = "生成提示词", description = "根据用户意图生成适合图片/视频的提示词")
    @PostMapping("/generate-prompt")
    public ApiResponse<Map<String, Object>> generatePrompt(
            @RequestBody Map<String, String> body) {
        String intent = body.getOrDefault("intent", "");
        String mediaType = body.getOrDefault("mediaType", "image");
        var result = promptGenerationService.generatePrompt(intent, mediaType);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "生成示意图", description = "为知识点生成示意图")
    @PostMapping("/generate")
    public ApiResponse<MediaResult> generate(
            @RequestParam String kpId,
            @RequestParam(required = false) String prompt) {
        return ApiResponse.ok(mediaGenerationService.generate(kpId, prompt));
    }

    @Operation(summary = "生成视频", description = "为知识点生成教学视频（Agnes AI 异步生成）")
    @PostMapping("/generate-video")
    public ApiResponse<MediaResult> generateVideo(
            @RequestParam String kpId,
            @RequestParam(required = false) String prompt) {
        return ApiResponse.ok(mediaGenerationService.generateVideo(kpId, prompt));
    }

    @Operation(summary = "自由生图测试", description = "直接用 prompt 生成图片，不需要知识点 ID")
    @PostMapping("/test-image")
    public ApiResponse<java.util.Map<String, Object>> testImage(
            @RequestBody java.util.Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "教学示意图，清晰简洁");
        String imageUrl = mediaGenerationService.generateImageByPrompt(prompt);
        return ApiResponse.ok(java.util.Map.of(
                "success", true,
                "imageUrl", imageUrl,
                "prompt", prompt
        ));
    }

    @Operation(summary = "自由生视频测试", description = "直接用 prompt 生成视频，不需要知识点 ID")
    @PostMapping("/test-video")
    public ApiResponse<java.util.Map<String, Object>> testVideo(
            @RequestBody java.util.Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "教学演示视频");
        String videoUrl = mediaGenerationService.generateVideoByPrompt(prompt);
        return ApiResponse.ok(java.util.Map.of(
                "success", true,
                "videoUrl", videoUrl,
                "prompt", prompt
        ));
    }

    @Operation(summary = "获取媒体文件", description = "按资源ID获取生成的媒体文件")
    @GetMapping("/{id}")
    public ResponseEntity<?> getMedia(@PathVariable Long id) {
        Path filePath = mediaGenerationService.getMediaPath(id);
        if (filePath == null || !filePath.toFile().exists()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("媒体文件尚未生成。请先通过 /api/media/generate 或 /api/media/generate-video 生成。");
        }

        try {
            byte[] bytes = Files.readAllBytes(filePath);
            String mime = Files.probeContentType(filePath);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(mime != null ? mime : "application/octet-stream"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    .body(bytes);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
