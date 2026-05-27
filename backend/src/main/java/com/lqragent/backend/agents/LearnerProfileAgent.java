package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.learnerprofile.service.LearnerProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LearnerProfileAgent implements Agent {

    private final LearnerProfileService learnerProfileService;

    @Override
    public String agentId() { return "learnerprofile"; }

    @Override
    public AgentResult process(AgentTask task) {
        Long userId = task.getUserId();
        var summary = learnerProfileService.getSummary(userId);
        return AgentResult.builder()
                .success(true)
                .data(summary != null ? Map.of("summary", summary) : Map.of())
                .build();
    }
}
