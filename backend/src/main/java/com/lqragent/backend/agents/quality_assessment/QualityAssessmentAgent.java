package com.lqragent.backend.agents.quality_assessment;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentIds;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.agent.ToolRegistry;
import com.lqragent.backend.agent.ToolSchema;
import com.lqragent.backend.agents.quality_assessment.service.QualityAssessmentService;
import com.lqragent.backend.agents.resource_generation.entity.ResourceItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class QualityAssessmentAgent implements Agent {

    private final QualityAssessmentService qualityAssessmentService;

    @Override
    public String agentId() { return AgentIds.QUALITY_ASSESSMENT; }

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

    @Override
    public String getSystemPrompt(AgentTask task) {
        return "你是内容质检员。按顺序检查：格式 → 敏感词 → 学术规范 → LLM 事实校验。";
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("assess_full", "全量检查资源内容（4层）",
                ToolSchema.params(Map.of(
                    "title", ToolSchema.stringParam("资源标题", ""),
                    "content", ToolSchema.stringParam("资源内容", "要检查的文本"),
                    "resourceType", ToolSchema.stringParam("资源类型", "LESSON/QUIZ/CODE_CASE/MIND_MAP/EXTENDED_READING")
                ), "content"))
        );
    }

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register(agentId(), "assess_full", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            ResourceItem item = ResourceItem.builder()
                    .title((String) p.getOrDefault("title", ""))
                    .content((String) p.getOrDefault("content", ""))
                    .resourceType((String) p.getOrDefault("resourceType", "LESSON"))
                    .build();
            var result = qualityAssessmentService.assessFull(item);
            return Map.of("passed", result.passed(), "failures", result.failures(), "summary", result.summary());
        });
    }
}
