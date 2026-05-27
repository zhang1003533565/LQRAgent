package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.learningpath.service.LearningPathService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LearningPathAgent implements Agent {

    private final LearningPathService learningPathService;

    @Override
    public String agentId() { return "learningpath"; }

    @Override
    public AgentResult process(AgentTask task) {
        Long userId = task.getUserId();
        String goal = (String) task.getPayload().getOrDefault("goal", "");
        var dto = learningPathService.generatePath(userId, goal, null);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("pathId", dto.getPathId(), "goal", dto.getGoal()))
                .build();
    }
}
