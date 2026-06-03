package com.lqragent.backend.uploadqueue.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.storage.FileCategories;
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
        String ext = FileCategories.extractExt(originalName);
        String category = FileCategories.categorize(ext);
        String key = category + "/" + userId + "/" + UUID.randomUUID() + "_" + originalName;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        qiniuStorageService.upload(key, file.getBytes(), contentType);

        // filePath 存七牛云 object key
        KbUploadTask task = uploadQueueService.enqueue(userId, originalName, key, scope);
        uploadQueueService.processImmediatelyAsync(task);

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

    @Operation(summary = "测试七牛云连接", description = "验证 AK/SK 和 Bucket 是否可用")
    @PostMapping("/test-qiniu")
    public ApiResponse<Map<String, Object>> testQiniu() {
        Map<String, Object> result = new java.util.HashMap<>();
        try {
            String testKey = "_test/connection_" + System.currentTimeMillis() + ".txt";
            qiniuStorageService.upload(testKey, "ok".getBytes(), "text/plain");
            qiniuStorageService.delete(testKey);
            result.put("success", true);
            result.put("message", "七牛云连接正常，bucket 可写可删");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ApiResponse.ok(result);
    }
}
