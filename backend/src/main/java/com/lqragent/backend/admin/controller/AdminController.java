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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "管理后台", description = "系统配置、模型管理、用户管理（仅 ADMIN 角色）")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ModelConfigService modelConfigService;
    private final UploadQueueService uploadQueueService;

    @Operation(summary = "系统状态总览", description = "返回后端端口、AI 服务连通性、用户/任务统计")
    @GetMapping("/status")
    public ApiResponse<AdminStatusDto> status() {
        return ApiResponse.ok(adminService.getStatus());
    }

    @Operation(summary = "获取大模型配置", description = "返回当前 LLM 和嵌入模型的配置（Key 已脱敏）")
    @GetMapping("/model-config")
    public ApiResponse<ModelConfigDto> getModelConfig() {
        return ApiResponse.ok(modelConfigService.getModelConfig());
    }

    @Operation(summary = "保存大模型配置", description = "保存 LLM/嵌入模型配置，可同步写入 ai-server/.env")
    @PutMapping("/model-config")
    public ApiResponse<ModelConfigDto> saveModelConfig(@RequestBody ModelConfigSaveRequest request) {
        return ApiResponse.ok(modelConfigService.saveModelConfig(request));
    }

    @Operation(summary = "测试大模型连通性", description = "用当前配置发送一条 ping 消息，验证 API 是否可用")
    @PostMapping("/model-config/test-llm")
    public ApiResponse<Map<String, Object>> testLlmConfig() {
        return ApiResponse.ok(modelConfigService.testLlmConnection());
    }

    @Operation(summary = "列出所有系统配置", description = "返回 sys_config 表中的所有配置项")
    @GetMapping("/config")
    public ApiResponse<List<SysConfigDto>> listConfig() {
        return ApiResponse.ok(adminService.listConfigs());
    }

    @Operation(summary = "保存单条系统配置", description = "新增或更新 sys_config 中的一条配置")
    @PutMapping("/config/{key}")
    public ApiResponse<SysConfigDto> saveConfig(
            @Parameter(description = "配置键名") @PathVariable String key,
            @Valid @RequestBody SysConfigSaveRequest request) {
        return ApiResponse.ok(adminService.saveConfig(key, request.getConfigValue(), request.getRemark()));
    }

    @Operation(summary = "删除系统配置", description = "从 sys_config 中删除一条配置")
    @DeleteMapping("/config/{key}")
    public ApiResponse<Void> deleteConfig(
            @Parameter(description = "配置键名") @PathVariable String key) {
        adminService.deleteConfig(key);
        return ApiResponse.ok();
    }

    @Operation(summary = "探测 AI 服务连通性", description = "调用 ai-server 健康检查接口")
    @PostMapping("/ai/ping")
    public ApiResponse<Map<String, Object>> pingAi() {
        AdminStatusDto status = adminService.getStatus();
        boolean ok = adminService.pingAiServer();
        return ApiResponse.ok(Map.of("reachable", ok, "baseUrl", status.getAiServerBaseUrl()));
    }

    @Operation(summary = "用户列表", description = "返回系统中所有用户")
    @GetMapping("/users")
    public ApiResponse<List<AdminUserDto>> listUsers() {
        return ApiResponse.ok(adminService.listUsers());
    }

    @Operation(summary = "上传任务列表（全局）", description = "查询所有用户的上传任务，最近优先")
    @GetMapping("/upload/tasks")
    public ApiResponse<List<KbUploadTask>> listUploadTasks(
            @Parameter(description = "返回条数，默认 50，最大 200") @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return ApiResponse.ok(uploadQueueService.listRecent(safeLimit));
    }

    @Operation(summary = "手动处理一条上传", description = "触发 worker 处理 PENDING 状态的待处理上传任务")
    @PostMapping("/upload/process")
    public ApiResponse<Map<String, Boolean>> processUpload() {
        boolean processed = adminService.processOneUpload();
        return ApiResponse.ok(Map.of("processed", processed));
    }
}
