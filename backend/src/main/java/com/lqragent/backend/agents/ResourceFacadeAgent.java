package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.resourcefacade.dto.ResourceGenerateRequest;
import com.lqragent.backend.resourcefacade.service.ResourceFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ResourceFacadeAgent implements Agent {

    private final ResourceFacadeService resourceFacadeService;

    @Override
    public String agentId() { return "resourcefacade"; }

    @Override
    public AgentResult process(AgentTask task) {
        Map<String, Object> payload = task.getPayload();
        String kpId = (String) payload.getOrDefault("kpId", "");
        String resourceType = (String) payload.getOrDefault("resourceType", "LESSON");

        var request = ResourceGenerateRequest.builder().kpId(kpId).resourceType(resourceType).build();
        var response = resourceFacadeService.generate(request);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("resourceId", response.getResourceId(),
                        "resourceType", response.getResourceType(),
                        "content", response.getContent()))
                .build();
    }
}
