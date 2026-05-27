package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.orchestrator.service.OrchestratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrchestratorAgent implements Agent {

    private final OrchestratorService orchestratorService;

    @Override
    public String agentId() { return "orchestrator"; }

    @Override
    public AgentResult process(AgentTask task) {
        String message = (String) task.getPayload().getOrDefault("message", "");
        var intent = orchestratorService.determineIntent(message);
        return AgentResult.builder()
                .success(true)
                .data(Map.of(
                        "intent", intent.getIntent(),
                        "label", intent.getLabel(),
                        "confidence", intent.getConfidence(),
                        "actionable", intent.isActionable()))
                .build();
    }
}
