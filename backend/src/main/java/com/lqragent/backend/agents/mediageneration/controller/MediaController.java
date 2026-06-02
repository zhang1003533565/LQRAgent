package com.lqragent.backend.agents.media_generation.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.agents.media_generation.dto.MediaResult;
import com.lqragent.backend.agents.media_generation.service.MediaGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;

@Tag(name = "媒体文件", description = "生成示意图、视频等媒体资源访问")
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final MediaGenerationService mediaGenerationService;

    @Operation(summary = "生成示意图", description = "为知识点生成示意图（P1 mock 实现）")
    @PostMapping("/generate")
    public ApiResponse<MediaResult> generate(
            @RequestParam String kpId,
            @RequestParam(required = false) String prompt) {
        return ApiResponse.ok(mediaGenerationService.generate(kpId, prompt));
    }

    @Operation(summary = "获取媒体文件", description = "按资源ID获取生成的媒体文件")
    @GetMapping("/{id}")
    public ResponseEntity<?> getMedia(@PathVariable Long id) {
        Path filePath = mediaGenerationService.getMediaPath(id);
        if (filePath == null || !filePath.toFile().exists()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("媒体文件尚未生成（P1 占位阶段）。实际文件将在接入 AI 生图 API 后可用。");
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
