package com.lqragent.backend.admin.controller;

import com.lqragent.backend.core.agent.AgentBus;
import com.lqragent.backend.core.agent.AgentIds;
import com.lqragent.backend.core.agent.AgentResult;
import com.lqragent.backend.core.agent.AgentTask;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.core.session.SessionContext;
import com.lqragent.backend.chat.entity.AgentRunLog;
import com.lqragent.backend.agents.effectassessment.service.EffectAssessmentService;
import com.lqragent.backend.agents.learningpath.service.LearningPathService;
import com.lqragent.backend.agents.learningpath.dto.LearningPathDto;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateResponse;
import com.lqragent.backend.agents.resourcegeneration.service.ResourceGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Agent 测试控制台 REST API — 免认证，直接测试各 Agent。
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class AgentTestController {

    private final AgentBus agentBus;
    private final LearningPathService learningPathService;
    private final ResourceGenerationService resourceGenerationService;
    private final EffectAssessmentService effectAssessmentService;

    // ===== 通用 Agent 测试 =====

    /** 通用：指定 agentType + message，走完整 AgentEngine 推理循环 */
    @PostMapping("/agent")
    public Map<String, Object> testAgent(@RequestBody Map<String, String> body) {
        String agentType = body.getOrDefault("agentType", AgentIds.ORCHESTRATOR);
        String message = body.getOrDefault("message", "");
        String sessionId = body.getOrDefault("sessionId", "test-" + System.nanoTime());

        RequestContext.init(1L);

        AgentTask task = AgentTask.builder()
                .agentType(agentType)
                .userId(1L)
                .sessionId(sessionId)
                .payload(Map.of("message", message))
                .build();

        long start = System.currentTimeMillis();
        AgentResult result = agentBus.dispatch(task).join();
        long duration = System.currentTimeMillis() - start;

        return Map.of(
                "success", result.isSuccess(),
                "agentType", agentType,
                "data", result.getData() != null ? result.getData() : Map.of(),
                "errorMessage", result.getErrorMessage() != null ? result.getErrorMessage() : "",
                "durationMs", duration,
                "taskId", task.getTaskId()
        );
    }

    // ===== 快捷测试端点 =====

    /** 生成学习路径 */
    @PostMapping("/path")
    public Map<String, Object> testPath(@RequestBody Map<String, String> body) {
        String goal = body.getOrDefault("goal", "Python装饰器");
        String currentKpId = body.getOrDefault("currentKpId", "");

        RequestContext.init(1L);
        long start = System.currentTimeMillis();

        try {
            LearningPathDto path = learningPathService.generatePath(1L, goal,
                    currentKpId.isBlank() ? null : currentKpId);
            long duration = System.currentTimeMillis() - start;

            return Map.of(
                    "success", true,
                    "pathId", path.getPathId(),
                    "goal", path.getGoal(),
                    "nodes", path.getNodes(),
                    "planDescription", path.getPlanDescription(),
                    "durationMs", duration
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 生成资源 */
    @PostMapping("/resource")
    public Map<String, Object> testResource(@RequestBody Map<String, String> body) {
        String kpId = body.getOrDefault("kpId", "kp_intro");
        String resourceType = body.getOrDefault("resourceType", "LESSON");

        RequestContext.init(1L);
        long start = System.currentTimeMillis();

        try {
            ResourceGenerateRequest req = ResourceGenerateRequest.builder()
                    .kpId(kpId)
                    .resourceType(resourceType)
                    .build();
            ResourceGenerateResponse resp = resourceGenerationService.generate(req);
            long duration = System.currentTimeMillis() - start;

            return Map.of(
                    "success", true,
                    "resourceId", resp.getResourceId(),
                    "kpId", resp.getKpId(),
                    "resourceType", resp.getResourceType(),
                    "title", resp.getTitle(),
                    "content", resp.getContent(),
                    "existingCount", resp.getExistingCount(),
                    "durationMs", duration
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    /** 模拟答题提交 */
    @PostMapping("/quiz-submit")
    public Map<String, Object> testQuizSubmit(@RequestBody Map<String, Object> body) {
        String kpId = (String) body.getOrDefault("kpId", "kp_intro");
        int score = body.containsKey("score") ? ((Number) body.get("score")).intValue() : 30;
        boolean correct = body.containsKey("correct") ? (Boolean) body.get("correct") : false;

        RequestContext.init(1L);

        try {
            effectAssessmentService.evaluateQuizResult(1L, kpId, score, correct);
            return Map.of(
                    "success", true,
                    "message", "答题评估完成，薄弱节点已处理",
                    "kpId", kpId,
                    "score", score,
                    "correct", correct
            );
        } catch (Exception e) {
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ===== 管理端点 =====

    /** 列出所有已注册 Agent */
    @GetMapping("/agents")
    public Map<String, Object> listAgents() {
        return Map.of("agents", agentBus.listAgents(), "count", agentBus.agentCount());
    }

    /** 查看会话上下文 */
    @GetMapping("/session/{id}")
    public Map<String, Object> getSession(@PathVariable String id) {
        List<Map<String, Object>> messages = SessionContext.getMessages(id);
        return Map.of(
                "sessionId", id,
                "messageCount", messages.size(),
                "messages", messages
        );
    }

    /** 清空会话上下文 */
    @PostMapping("/session/{id}/clear")
    public Map<String, Object> clearSession(@PathVariable String id) {
        SessionContext.reset(id);
        return Map.of("sessionId", id, "cleared", true);
    }

    /** 查看执行日志（来自 AgentBus） */
    @GetMapping("/runs")
    public Map<String, Object> getRuns() {
        List<AgentRunLog> logs = agentBus.getRunLogs();
        return Map.of("runs", logs, "count", logs.size());
    }
}
