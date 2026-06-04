package com.lqragent.backend.admin.controller;

import com.lqragent.backend.orchestrator.OrchestratorCore;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.core.session.RequestContext;
import com.lqragent.backend.agents.effectassessment.service.EffectAssessmentService;
import com.lqragent.backend.agents.learningpath.service.LearningPathService;
import com.lqragent.backend.agents.learningpath.dto.LearningPathDto;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateRequest;
import com.lqragent.backend.agents.resourcegeneration.dto.ResourceGenerateResponse;
import com.lqragent.backend.agents.resourcegeneration.service.ResourceGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Agent 测试控制台 REST API — 免认证，直接测试各 Agent。
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class AgentTestController {

    private final OrchestratorCore orchestratorCore;
    private final LearningPathService learningPathService;
    private final ResourceGenerationService resourceGenerationService;
    private final EffectAssessmentService effectAssessmentService;

    // ===== 通用 Agent 测试 =====

    /** 通用：通过 OrchestratorCore 意图识别路由 */
    @PostMapping("/agent")
    public Map<String, Object> testAgent(@RequestBody Map<String, String> body) {
        String message = body.getOrDefault("message", "");

        long start = System.currentTimeMillis();
        Map<String, Object> result = orchestratorCore.handleChatMessage("1", message);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        data.put("route", result.get("route"));
        data.put("response", result.get("response"));
        data.put("agent", result.get("agent"));
        data.put("durationMs", duration);
        return data;
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
}
