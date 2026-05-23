package com.lqragent.backend.uploadqueue.controller;

import com.lqragent.backend.common.dto.ApiResponse;
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

import java.io.File;
import java.util.List;

@Tag(name = "上传", description = "文件上传与队列查询")
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadQueueController {

    private final UploadQueueService uploadQueueService;
    private final CurrentUserService currentUserService;

    @Operation(summary = "上传文件", description = "上传资料文件进入知识库处理队列")
    @PostMapping
    public ApiResponse<KbUploadTask> upload(
            @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "知识库范围：PERSONAL 个人 / PUBLIC 公开") @RequestParam(value = "scope", defaultValue = "PERSONAL") KbScope scope,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) throws Exception {

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

    @Operation(summary = "查询我的上传任务", description = "返回当前用户的所有上传任务及状态")
    @GetMapping("/tasks")
    public ApiResponse<List<KbUploadTask>> listTasks(
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = currentUserService.requireUserId(userDetails);
        return ApiResponse.ok(uploadQueueService.listByUser(userId));
    }
}
