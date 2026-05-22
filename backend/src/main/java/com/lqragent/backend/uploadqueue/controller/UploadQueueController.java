package com.lqragent.backend.uploadqueue.controller;

import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.KbScope;
import com.lqragent.backend.uploadqueue.service.UploadQueueService;
import com.lqragent.backend.user.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

/**
 * 上传队列接口。
 * POST /api/upload          → 上传文件，入队
 * GET  /api/upload/tasks    → 查询当前用户的任务列表
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadQueueController {

    private final UploadQueueService uploadQueueService;
    private final CurrentUserService currentUserService;

    @PostMapping
    public ApiResponse<KbUploadTask> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "scope", defaultValue = "PERSONAL") KbScope scope,
            @AuthenticationPrincipal UserDetails userDetails) throws Exception {

        // 临时保存到系统临时目录，worker 处理时再读取
        File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
        file.transferTo(tempFile);

        Long userId = currentUserService.requireUserId(userDetails);

        KbUploadTask task = uploadQueueService.enqueue(
                userId,
                file.getOriginalFilename(),
                tempFile.getAbsolutePath(),
                scope);

        return ApiResponse.ok(task);
    }

    @GetMapping("/tasks")
    public ApiResponse<List<KbUploadTask>> listTasks(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(uploadQueueService.listByUser(userId));
    }
}
