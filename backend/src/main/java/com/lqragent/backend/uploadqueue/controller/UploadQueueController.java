package com.lqragent.backend.uploadqueue.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.storage.QiniuStorageService;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.KbScope;
import com.lqragent.backend.uploadqueue.service.UploadQueueService;
import com.lqragent.backend.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Tag(name = "上传", description = "文件上传与队列查询")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadQueueController {

    private final UploadQueueService uploadQueueService;
    private final CurrentUserService currentUserService;
    private final QiniuStorageService qiniuStorageService;

    @Operation(summary = "上传文件", description = "文件直传七牛云对象存储，不落本地磁盘")
    @PostMapping
    public ApiResponse<KbUploadTask> upload(
            @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "知识库范围：PERSONAL 个人 / PUBLIC 公开") @RequestParam(value = "scope", defaultValue = "PERSONAL") KbScope scope,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        Long userId = currentUserService.requireUserId(userDetails);

        // 按文件类型分类存储
        String originalName = file.getOriginalFilename();
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf(".") + 1).toLowerCase()
                : "";
        String category = categorizeFile(ext);
        String key = category + "/" + userId + "/" + UUID.randomUUID() + "_" + originalName;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        qiniuStorageService.upload(key, file.getBytes(), contentType);

        // filePath 存七牛云 object key
        KbUploadTask task = uploadQueueService.enqueue(userId, originalName, key, scope);

        return ApiResponse.ok(task);
    }

    @Operation(summary = "查询我的上传任务", description = "返回当前用户的所有上传任务及状态")
    @GetMapping("/tasks")
    public ApiResponse<List<KbUploadTask>> listTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(uploadQueueService.listByUser(userId));
    }

    @Operation(summary = "获取文件下载链接", description = "返回七牛云预签名临时下载 URL（有效期 1 小时）")
    @GetMapping("/file/{id}")
    public ApiResponse<Map<String, String>> getFileUrl(@PathVariable Long id) {
        KbUploadTask task = uploadQueueService.getTaskById(id);
        String url = qiniuStorageService.getPresignedUrl(task.getFilePath());
        return ApiResponse.ok(Map.of("url", url, "fileName", task.getFileName()));
    }

    /**
     * 按文件扩展名分类到不同目录。
     */
    private String categorizeFile(String ext) {
        return switch (ext) {
            case "pdf" -> "documents/pdf";
            case "doc", "docx" -> "documents/word";
            case "ppt", "pptx" -> "documents/ppt";
            case "xls", "xlsx" -> "documents/excel";
            case "md", "txt", "rst" -> "documents/text";
            case "py", "java", "kt", "js", "ts", "go", "rs", "c", "cpp", "h" -> "code";
            case "png", "jpg", "jpeg", "gif", "webp", "svg" -> "images";
            case "mp4", "avi", "mov" -> "media/video";
            case "mp3", "wav", "ogg" -> "media/audio";
            case "json", "yaml", "yml", "toml", "xml", "csv" -> "data";
            default -> "documents/other";
        };
    }
}
