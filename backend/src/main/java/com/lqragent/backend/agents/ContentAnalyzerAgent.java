package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.contentanalyzer.service.ContentAnalyzerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContentAnalyzerAgent implements Agent {

    private final ContentAnalyzerService contentAnalyzerService;

    @Override
    public String agentId() { return "contentanalyzer"; }

    @Override
    public AgentResult process(AgentTask task) {
        Map<String, Object> payload = task.getPayload();
        String filePath = (String) payload.getOrDefault("filePath", "");
        String fileName = (String) payload.getOrDefault("fileName", "");

        var result = contentAnalyzerService.analyze(filePath, fileName);
        return AgentResult.builder()
                .success(true)
                .data(Map.of("summary", result.summary(), "mappedKpIds", result.mappedKpIds()))
                .build();
    }
}
