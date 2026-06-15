package com.lqragent.backend.admin.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lqragent.backend.admin.dto.AdminStatusDto;
import com.lqragent.backend.admin.dto.AdminUserDto;
import com.lqragent.backend.admin.dto.ModelConfigDto;
import com.lqragent.backend.admin.dto.ModelConfigSaveRequest;
import com.lqragent.backend.admin.dto.SysConfigDto;
import com.lqragent.backend.admin.dto.SysConfigSaveRequest;
import com.lqragent.backend.admin.repository.AgentRunLogRepository;
import com.lqragent.backend.admin.service.AdminService;
import com.lqragent.backend.admin.service.ModelConfigService;
import com.lqragent.backend.agents.base.AgentRegistry;
import com.lqragent.backend.agents.resourcegeneration.entity.ResourceItem;
import com.lqragent.backend.agents.resourcegeneration.repository.ResourceItemRepository;
import com.lqragent.backend.agents.learn.path.entity.LearningPath;
import com.lqragent.backend.agents.learn.path.entity.LearningPathStep;
import com.lqragent.backend.agents.learn.path.repository.LearningPathRepository;
import com.lqragent.backend.agents.learn.path.repository.LearningPathStepRepository;
import com.lqragent.backend.agents.learnerprofile.dto.ProfileSummaryDto;
import com.lqragent.backend.agents.learnerprofile.repository.LearnerProfileRepository;
import com.lqragent.backend.chat.entity.AgentRunLog;
import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.quiz.repository.QuizRecordRepository;
import com.lqragent.backend.quiz.repository.StudyBehaviorRepository;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgeEdge;
import com.lqragent.backend.shared.knowledgegraph.entity.KnowledgePoint;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgeEdgeRepository;
import com.lqragent.backend.shared.knowledgegraph.repository.KnowledgePointRepository;
import com.lqragent.backend.storage.QiniuStorageService;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask.KbScope;
import com.lqragent.backend.uploadqueue.service.UploadQueueService;
import com.lqragent.backend.user.service.CurrentUserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;

@Slf4j
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
    private final OrchestratorCore orchestratorCore;
    private final QuizRecordRepository quizRecordRepo;
    private final StudyBehaviorRepository studyBehaviorRepo;
    private final QiniuStorageService qiniuStorageService;
    private final CurrentUserService currentUserService;
    private final AgentRegistry agentRegistry;

    @Value("${ai-server.host:http://localhost:8001}")
    private String aiServerHost;

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

    @Operation(summary = "知识图谱数据", description = "返回知识点节点 + 依赖边，可按 subject 过滤")
    @GetMapping("/knowledge-graph")
    public ApiResponse<Map<String, Object>> getKnowledgeGraph(
            @Parameter(description = "按科目过滤") @RequestParam(required = false) String subject) {
        List<KnowledgePoint> nodes = subject != null && !subject.isBlank()
                ? knowledgePointRepo.findBySubject(subject)
                : knowledgePointRepo.findAll();
        List<KnowledgeEdge> edges = knowledgeEdgeRepo.findAll();
        List<String> subjects = knowledgePointRepo.findDistinctSubjects();
        Map<String, Object> result = new HashMap<>();
        result.put("nodes", nodes);
        result.put("edges", edges);
        result.put("nodeCount", nodes.size());
        result.put("edgeCount", edges.size());
        result.put("subjects", subjects);
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

    @Operation(summary = "资源列表", description = "查询所有已生成的讲义/题目/代码/示意图，支持按科目/类型过滤")
    @GetMapping("/resources")
    public ApiResponse<Map<String, Object>> listAllResources(
            @Parameter(description = "按类型过滤") @RequestParam(required = false) String type,
            @Parameter(description = "按知识点过滤") @RequestParam(required = false) String kpId,
            @Parameter(description = "按科目过滤") @RequestParam(required = false) String subject) {
        List<ResourceItem> items;
        if (subject != null && !subject.isBlank()) {
            items = resourceItemRepo.findBySubject(subject);
            if (type != null) items = items.stream().filter(i -> i.getResourceType().equals(type)).toList();
            if (kpId != null) items = items.stream().filter(i -> i.getKpId().equals(kpId)).toList();
        } else if (kpId != null && type != null) {
            items = resourceItemRepo.findByKpIdAndResourceType(kpId, type);
        } else if (kpId != null) {
            items = resourceItemRepo.findByKpId(kpId);
        } else if (type != null) {
            items = resourceItemRepo.findByResourceType(type);
        } else {
            items = resourceItemRepo.findAll();
        }
        List<String> subjects = resourceItemRepo.findDistinctSubjects();
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("subjects", subjects);
        result.put("total", items.size());
        return ApiResponse.ok(result);
    }

    @Operation(summary = "测试智能体", description = "发送测试消息到指定 Agent，返回执行结果。payload 为完整 payload map，可包含 message/kpId/goal 等字段")
    @SuppressWarnings("unchecked")
    @PostMapping("/agent-test")
    public ApiResponse<Map<String, Object>> testAgent(
            @RequestBody Map<String, Object> body,
            @Parameter(hidden = true) @AuthenticationPrincipal UserDetails userDetails) {
        String message = (String) body.getOrDefault("message", "");
        Long userId = currentUserService.requireUserId(userDetails);

        long start = System.currentTimeMillis();
        Map<String, Object> result = orchestratorCore.handleChatMessage(String.valueOf(userId), message);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("route", result.get("route"));
        data.put("response", result.get("response"));
        data.put("agent", result.get("agent"));
        data.put("durationMs", duration);
        return ApiResponse.ok(data);
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
        result.put("registeredAgents", new ArrayList<>(agentRegistry.getAllAgentIds()));
        result.put("agentCount", agentRegistry.size());
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

    @Operation(summary = "答题记录列表", description = "按时间倒序分页返回答题记录")
    @GetMapping("/quiz-records")
    public ApiResponse<Map<String, Object>> getQuizRecords(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，默认20") @RequestParam(defaultValue = "20") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        var p = quizRecordRepo.findAll(pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("items", p.getContent());
        result.put("total", p.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "学习行为记录列表", description = "按时间倒序分页返回学习行为")
    @GetMapping("/study-behaviors")
    public ApiResponse<Map<String, Object>> getStudyBehaviors(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数，默认20") @RequestParam(defaultValue = "20") int size) {
        var pageable = org.springframework.data.domain.PageRequest.of(
                Math.max(page - 1, 0), Math.min(Math.max(size, 1), 100),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        var p = studyBehaviorRepo.findAll(pageable);
        Map<String, Object> result = new HashMap<>();
        result.put("items", p.getContent());
        result.put("total", p.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "重试上传任务", description = "重新处理一条失败的上传任务")
    @PostMapping("/upload/process/{taskId}")
    public ApiResponse<?> retryUploadTask(@PathVariable Long taskId) {
        KbUploadTask task = uploadQueueService.getTaskById(taskId);
        uploadQueueService.processImmediatelyAsync(task);
        return ApiResponse.ok(Map.of("message", "已重新触发处理", "taskId", taskId));
    }

    @Operation(summary = "删除上传任务", description = "删除一条上传任务记录")
    @DeleteMapping("/upload/tasks/{taskId}")
    public ApiResponse<?> deleteUploadTask(@PathVariable Long taskId) {
        uploadQueueService.deleteTask(taskId);
        return ApiResponse.ok(Map.of("message", "已删除", "taskId", taskId));
    }

    @Operation(summary = "上传公共资料", description = "上传文件到公共知识库 kb-public，异步向量化")
    @PostMapping("/upload-public")
    public ApiResponse<?> uploadPublic(
            @Parameter(description = "要上传的文件") @RequestParam("file") MultipartFile file) {

        log.info("[Admin] upload-public: file={}, size={}, type={}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        try {
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                return ApiResponse.ok(Map.of("error", "文件名为空"));
            }
            String key = "uploads/" + UUID.randomUUID() + "_" + originalName;
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            log.info("[Admin] upload-public: key={}, size={}", key, file.getSize());
            qiniuStorageService.upload(key, file.getBytes(), contentType);
            log.info("[Admin] upload-public: uploaded to qiniu, key={}", key);

            // 入队 PUBLIC scope，异步处理（不阻塞）
            KbUploadTask task = uploadQueueService.enqueue(0L, originalName, key, KbScope.PUBLIC);
            uploadQueueService.processImmediatelyAsync(task);
            log.info("[Admin] upload-public: task created, id={}, async processing started", task.getId());

            return ApiResponse.ok(task);
        } catch (Exception e) {
            log.error("[Admin] upload-public failed: {}", e.getMessage(), e);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            return ApiResponse.fail(500, errMsg);
        }
    }

    /**
     * 触发知识库向量索引重建（Chroma）
     * 调用 ai-server 的 /api/v1/knowledge/{kbName}/reindex 端点
     */
    @Operation(summary = "重建知识库向量索引", description = "触发指定知识库的向量索引重建（Chroma）")
    @PostMapping("/knowledge-base/reindex")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> reindexKnowledgeBase(@RequestParam(defaultValue = "default") String kbName) {
        try {
            java.net.URI uri = new java.net.URI(aiServerHost + "/api/v1/knowledge/" + kbName + "/reindex");

            var client = java.net.http.HttpClient.newHttpClient();
            var request = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = new ObjectMapper().readValue(body, Map.class);
            log.info("[Admin] reindex KB '{}' triggered: status={}, taskId={}", kbName, response.statusCode(),
                    result.getOrDefault("task_id", "none"));

            return ApiResponse.ok(result);
        } catch (Exception e) {
            log.error("[Admin] reindex KB '{}' failed", kbName, e);
            return ApiResponse.fail(500, "重建索引失败: " + e.getMessage());
        }
    }
}
