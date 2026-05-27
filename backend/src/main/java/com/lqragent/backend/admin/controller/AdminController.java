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
import com.lqragent.backend.knowledgegraph.entity.KnowledgeEdge;
import com.lqragent.backend.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.knowledgegraph.repository.KnowledgeEdgeRepository;
import com.lqragent.backend.knowledgegraph.repository.KnowledgePointRepository;
import com.lqragent.backend.learnerprofile.dto.ProfileSummaryDto;
import com.lqragent.backend.learnerprofile.entity.LearnerProfile;
import com.lqragent.backend.learnerprofile.repository.LearnerProfileRepository;
import com.lqragent.backend.learningpath.entity.LearningPath;
import com.lqragent.backend.learningpath.entity.LearningPathStep;
import com.lqragent.backend.learningpath.repository.LearningPathRepository;
import com.lqragent.backend.learningpath.repository.LearningPathStepRepository;
import com.lqragent.backend.resourcefacade.entity.ResourceItem;
import com.lqragent.backend.resourcefacade.repository.ResourceItemRepository;
import com.lqragent.backend.agent.AgentBus;
import com.lqragent.backend.observability.entity.AgentRunLog;
import com.lqragent.backend.observability.repository.AgentRunLogRepository;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.service.UploadQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "管理后台", description = "系统配置、模型管理、用户管理（仅 ADMIN 角色）")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ModelConfigService modelConfigService;
    private final UploadQueueService uploadQueueService;
    private final LearnerProfileRepository learnerProfileRepo;
    private final KnowledgePointRepository knowledgePointRepo;
    private final KnowledgeEdgeRepository knowledgeEdgeRepo;
    private final LearningPathRepository learningPathRepo;
    private final LearningPathStepRepository learningPathStepRepo;
    private final ResourceItemRepository resourceItemRepo;
    private final AgentRunLogRepository agentRunLogRepo;
    private final AgentBus agentBus;

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

    // ===== 新增：画像 / 图谱 / 路径 / 资源 管理接口 =====

    @Operation(summary = "学习画像列表", description = "查询所有学生的画像数据")
    @GetMapping("/profiles")
    public ApiResponse<List<ProfileSummaryDto>> listProfiles() {
        List<ProfileSummaryDto> list = learnerProfileRepo.findAll().stream()
                .map(p -> ProfileSummaryDto.builder()
                        .id(p.getId()).userId(p.getUserId())
                        .knowledgeLevel(p.getKnowledgeLevel())
                        .learningGoal(p.getLearningGoal())
                        .cognitiveStyle(p.getCognitiveStyle())
                        .commonErrors(p.getCommonErrors())
                        .learningPace(p.getLearningPace())
                        .interestDirection(p.getInterestDirection())
                        .preferredResourceType(p.getPreferredResourceType())
                        .build())
                .collect(Collectors.toList());
        return ApiResponse.ok(list);
    }

    @Operation(summary = "知识图谱数据", description = "返回所有知识点节点 + 依赖边")
    @GetMapping("/knowledge-graph")
    public ApiResponse<Map<String, Object>> getKnowledgeGraph() {
        List<KnowledgePoint> nodes = knowledgePointRepo.findAll();
        List<KnowledgeEdge> edges = knowledgeEdgeRepo.findAll();
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "学习路径列表", description = "查询所有已生成的个性化学习路径")
    @GetMapping("/learning-paths")
    public ApiResponse<List<Map<String, Object>>> listLearningPaths() {
        List<LearningPath> paths = learningPathRepo.findAll();
        var result = paths.stream().map(p -> {
            List<LearningPathStep> steps = learningPathStepRepo.findByPathIdOrderByStepOrder(p.getId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("userId", p.getUserId());
            m.put("goal", p.getGoal());
            m.put("createdAt", p.getCreatedAt());
            m.put("stepCount", steps.size());
            m.put("completedCount", steps.stream().filter(s -> Boolean.TRUE.equals(s.getCompleted())).count());
            return m;
        }).collect(Collectors.toList());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "资源列表", description = "查询所有已生成的讲义/题目/代码/示意图")
    @GetMapping("/resources")
    public ApiResponse<List<ResourceItem>> listAllResources(
            @Parameter(description = "按类型过滤：LESSON/QUIZ/CODE_CASE/ILLUSTRATION") @RequestParam(required = false) String type,
            @Parameter(description = "按知识点过滤") @RequestParam(required = false) String kpId) {
        List<ResourceItem> items;
        if (kpId != null && type != null) {
            items = resourceItemRepo.findByKpIdAndResourceType(kpId, type);
        } else if (kpId != null) {
            items = resourceItemRepo.findByKpId(kpId);
        } else if (type != null) {
            items = resourceItemRepo.findByResourceType(type);
        } else {
            items = resourceItemRepo.findAll();
        }
        return ApiResponse.ok(items);
    }

    @Operation(summary = "智能体调用统计", description = "已注册智能体列表 + 调用次数/成功率/平均耗时")
    @GetMapping("/agent-stats")
    public ApiResponse<Map<String, Object>> getAgentStats() {
        List<AgentRunLog> all = agentRunLogRepo.findAll();
        var byAgent = all.stream().collect(Collectors.groupingBy(AgentRunLog::getAgent));
        var stats = byAgent.entrySet().stream().map(entry -> {
            String agent = entry.getKey();
            var logs = entry.getValue();
            long total = logs.size();
            long success = logs.stream().filter(l -> l.getStatus() == AgentRunLog.RunStatus.SUCCESS).count();
            long failed = logs.stream().filter(l -> l.getStatus() == AgentRunLog.RunStatus.FAILED).count();
            double avgDuration = logs.stream()
                    .filter(l -> l.getDurationMs() != null)
                    .mapToInt(AgentRunLog::getDurationMs)
                    .average().orElse(0);
            Map<String, Object> m = new HashMap<>();
            m.put("agent", agent);
            m.put("total", total);
            m.put("success", success);
            m.put("failed", failed);
            m.put("successRate", total > 0 ? String.format("%.1f%%", success * 100.0 / total) : "0%");
            m.put("avgDurationMs", Math.round(avgDuration));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("stats", stats);
        result.put("registeredAgents", agentBus.listAgents());
        result.put("agentCount", agentBus.agentCount());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "智能体运行日志（分页）", description = "按时间倒序返回智能体运行记录")
    @GetMapping("/agent-runs")
    public ApiResponse<Map<String, Object>> getAgentRuns(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，默认20") @RequestParam(defaultValue = "200") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        org.springframework.data.domain.Page<AgentRunLog> p = agentRunLogRepo.findAll(pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("items", p.getContent());
        result.put("total", p.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        return ApiResponse.ok(result);
    }
}
