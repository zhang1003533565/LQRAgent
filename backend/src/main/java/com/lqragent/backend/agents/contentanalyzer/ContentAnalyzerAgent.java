package com.lqragent.backend.agents.contentanalyzer;

import com.lqragent.backend.core.agent.Agent;
import com.lqragent.backend.core.agent.AgentIds;
import com.lqragent.backend.core.agent.AgentResult;
import com.lqragent.backend.core.agent.AgentTask;
import com.lqragent.backend.core.tool.ToolRegistry;
import com.lqragent.backend.core.tool.ToolSchema;
import com.lqragent.backend.agents.contentanalyzer.service.ContentAnalyzerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ContentAnalyzerAgent implements Agent {

    private final ContentAnalyzerService contentAnalyzerService;

    @Override
    public String agentId() { return AgentIds.CONTENT_ANALYZER; }

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

    @Override
    public String getSystemPrompt(AgentTask task) {
        return "你是内容分析专家。分析上传的文档内容，提取并关联到知识图谱。";
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("analyze", "分析文档并关联知识点",
                ToolSchema.params(Map.of(
                    "filePath", ToolSchema.stringParam("文件路径", "上传文件的路径"),
                    "fileName", ToolSchema.stringParam("文件名", "上传文件的名称")
                ), "filePath", "fileName"))
        );
    }

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register(agentId(), "analyze", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            String fp = (String) p.getOrDefault("filePath", "");
            String fn = (String) p.getOrDefault("fileName", "");
            var result = contentAnalyzerService.analyze(fp, fn);
            return Map.of("summary", result.summary(), "mappedKpIds", result.mappedKpIds());
        });
    }
}
