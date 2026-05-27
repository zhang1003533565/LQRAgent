package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.qualityassessment.service.QualityAssessmentService;
import com.lqragent.backend.resourcefacade.entity.ResourceItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class QualityAssessmentAgent implements Agent {

    private final QualityAssessmentService qualityAssessmentService;

    @Override
    public String agentId() { return "qualityassessment"; }

    @Override
    public AgentResult process(AgentTask task) {
        Map<String, Object> payload = task.getPayload();
        ResourceItem item = (ResourceItem) payload.get("resourceItem");
        if (item == null) {
            return AgentResult.builder().success(false).errorMessage("缺少 resourceItem").build();
        }
        boolean passed = qualityAssessmentService.assess(item);
        return AgentResult.builder()
                .success(passed)
                .data(Map.of("passed", passed, "message", passed ? "校验通过" : "内容校验失败"))
                .build();
    }
}
