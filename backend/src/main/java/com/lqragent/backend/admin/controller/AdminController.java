package com.lqragent.backend.admin.controller;

import com.lqragent.backend.admin.dto.AdminStatusDto;
import com.lqragent.backend.admin.dto.AdminUserDto;
import com.lqragent.backend.admin.dto.ModelConfigDto;
import com.lqragent.backend.admin.dto.ModelConfigSaveRequest;
import com.lqragent.backend.admin.dto.SysConfigDto;
import com.lqragent.backend.admin.dto.SysConfigSaveRequest;
import com.lqragent.backend.admin.service.AdminService;
import com.lqragent.backend.admin.service.ModelConfigService;
import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.service.UploadQueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 API（仅 ADMIN 角色）。
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ModelConfigService modelConfigService;
    private final UploadQueueService uploadQueueService;

    @GetMapping("/status")
    public ApiResponse<AdminStatusDto> status() {
        return ApiResponse.ok(adminService.getStatus());
    }

    @GetMapping("/model-config")
    public ApiResponse<ModelConfigDto> getModelConfig() {
        return ApiResponse.ok(modelConfigService.getModelConfig());
    }

    @PutMapping("/model-config")
    public ApiResponse<ModelConfigDto> saveModelConfig(@RequestBody ModelConfigSaveRequest request) {
        return ApiResponse.ok(modelConfigService.saveModelConfig(request));
    }

    @PostMapping("/model-config/test-llm")
    public ApiResponse<Map<String, Object>> testLlmConfig() {
        return ApiResponse.ok(modelConfigService.testLlmConnection());
    }

    @GetMapping("/config")
    public ApiResponse<List<SysConfigDto>> listConfig() {
        return ApiResponse.ok(adminService.listConfigs());
    }

    @PutMapping("/config/{key}")
    public ApiResponse<SysConfigDto> saveConfig(
            @PathVariable String key,
            @Valid @RequestBody SysConfigSaveRequest request) {
        return ApiResponse.ok(adminService.saveConfig(key, request.getConfigValue(), request.getRemark()));
    }

    @DeleteMapping("/config/{key}")
    public ApiResponse<Void> deleteConfig(@PathVariable String key) {
        adminService.deleteConfig(key);
        return ApiResponse.ok();
    }

    @PostMapping("/ai/ping")
    public ApiResponse<Map<String, Object>> pingAi() {
        AdminStatusDto status = adminService.getStatus();
        boolean ok = adminService.pingAiServer();
        return ApiResponse.ok(Map.of("reachable", ok, "baseUrl", status.getAiServerBaseUrl()));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserDto>> listUsers() {
        return ApiResponse.ok(adminService.listUsers());
    }

    @GetMapping("/upload/tasks")
    public ApiResponse<List<KbUploadTask>> listUploadTasks(
            @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return ApiResponse.ok(uploadQueueService.listRecent(safeLimit));
    }

    @PostMapping("/upload/process")
    public ApiResponse<Map<String, Boolean>> processUpload() {
        boolean processed = adminService.processOneUpload();
        return ApiResponse.ok(Map.of("processed", processed));
    }
}
