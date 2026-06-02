package com.lqragent.backend.agents.mediageneration;

import com.lqragent.backend.core.agent.Agent;
import com.lqragent.backend.core.agent.AgentIds;
import com.lqragent.backend.core.agent.AgentResult;
import com.lqragent.backend.core.agent.AgentTask;
import com.lqragent.backend.core.tool.ToolRegistry;
import com.lqragent.backend.core.tool.ToolSchema;
import com.lqragent.backend.agents.mediageneration.service.MediaGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 媒体生成智能体：示意图、教学配图等。
 */
@Component
@RequiredArgsConstructor
public class MediaGenerationAgent implements Agent {

    private final MediaGenerationService mediaGenerationService;

    @Override
    public String agentId() {
        return AgentIds.MEDIA_GENERATION;
    }

    @Override
    public AgentResult process(AgentTask task) {
        String kpId = (String) task.getPayload().getOrDefault("kpId", "kp_intro");
        String prompt = (String) task.getPayload().getOrDefault("message", "");
        var result = mediaGenerationService.generate(kpId, prompt);
        return AgentResult.builder()
                .success(true)
                .data(Map.of(
                        "resourceId", result.getResourceId(),
                        "url", result.getMediaUrl() != null ? result.getMediaUrl() : ""
                ))
                .build();
    }

    @Override
    public String getSystemPrompt(AgentTask task) {
        return "你是教学示意图生成专家。根据用户描述调用 generate_media 生成配图。";
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
                ToolSchema.of("generate_media", "生成知识点示意图",
                        ToolSchema.params(Map.of(
                                "kpId", ToolSchema.stringParam("知识点ID", "kp_intro"),
                                "prompt", ToolSchema.stringParam("画面描述", "")
                        ), "kpId"))
        );
    }

    @Override
    public void registerTools(ToolRegistry registry) {
        registry.register(agentId(), "generate_media", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            String kpId = (String) p.getOrDefault("kpId", "kp_intro");
            String prompt = (String) p.getOrDefault("prompt", "");
            var result = mediaGenerationService.generate(kpId, prompt);
            return Map.of(
                    "status", "success",
                    "resourceId", result.getResourceId(),
                    "url", result.getMediaUrl() != null ? result.getMediaUrl() : "",
                    "message", "示意图已生成并存入数据库"
            );
        });
    }
}
