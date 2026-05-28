package com.lqragent.backend.agents.effect_assessment;

import com.lqragent.backend.framework.Agent;
import com.lqragent.backend.framework.AgentIds;
import com.lqragent.backend.framework.AgentResult;
import com.lqragent.backend.framework.AgentTask;
import com.lqragent.backend.framework.ToolRegistry;
import com.lqragent.backend.framework.ToolSchema;
import com.lqragent.backend.agents.effect_assessment.service.EffectAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EffectAssessmentAgent implements Agent {

    private final EffectAssessmentService effectAssessmentService;

    @Override
    public String agentId() { return AgentIds.EFFECT_ASSESSMENT; }

    @Override
    public AgentResult process(AgentTask task) {
        Map<String, Object> payload = task.getPayload();
        Long userId = task.getUserId();
        String kpId = (String) payload.getOrDefault("kpId", "");
        int score = (int) payload.getOrDefault("score", 0);
        boolean correct = (boolean) payload.getOrDefault("correct", false);

        effectAssessmentService.evaluateQuizResult(userId, kpId, score, correct);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("evaluated", true, "kpId", kpId))
                .build();
    }

    @Override
    public String getSystemPrompt(AgentTask task) {
        return "你是学习效果评估专家。分析学生的答题和行为数据，评估薄弱点并调整学习路径。";
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("evaluate_quiz", "评估答题结果，低分时插入复习节点",
                ToolSchema.params(Map.of(
                    "userId", ToolSchema.stringParam("学生ID", ""),
                    "kpId", ToolSchema.stringParam("知识点ID", ""),
                    "score", Map.of("type", "integer", "description", "得分0-100"),
                    "correct", Map.of("type", "boolean", "description", "是否正确")
                ), "userId", "kpId", "score", "correct")),
            ToolSchema.of("analyze_weakness", "分析学生薄弱点",
                ToolSchema.params(Map.of("userId", ToolSchema.stringParam("学生ID", "")), "userId"))
        );
    }

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register(agentId(), "evaluate_quiz", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            Long uid = Long.valueOf((String) p.getOrDefault("userId", "0"));
            String kpId = (String) p.getOrDefault("kpId", "");
            int score = ((Number) p.getOrDefault("score", 0)).intValue();
            boolean correct = (boolean) p.getOrDefault("correct", false);
            effectAssessmentService.evaluateQuizResult(uid, kpId, score, correct);
            return Map.of("evaluated", true);
        });
        registry.register(agentId(), "analyze_weakness", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            Long uid = Long.valueOf((String) p.getOrDefault("userId", "0"));
            return effectAssessmentService.analyzeWeakness(uid);
        });
    }
}
