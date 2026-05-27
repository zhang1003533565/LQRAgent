package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.effectassessment.service.EffectAssessmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class EffectAssessmentAgent implements Agent {

    private final EffectAssessmentService effectAssessmentService;

    @Override
    public String agentId() { return "effectassessment"; }

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
}
