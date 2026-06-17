package com.lqragent.backend.agents.mediageneration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.agents.mediageneration.tools.GenerateMediaTool;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.card.ToolSpec;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class MediaGenAgent extends BaseAgent {

    private final GenerateMediaTool tool;

    public MediaGenAgent(RedisStreamsService streams, LlmClient llmClient,
                          AgentToolRegistry toolRegistry, GenerateMediaTool tool,
                          PromptService promptService) {
        super(AgentIds.MEDIA_GEN, streams, llmClient, toolRegistry, promptService);
        this.tool = tool;
    }

    @Override
    protected String getSystemPrompt() {
        return getManagedPrompt();
    }

    @Override
    protected List<AgentTool> getTools() {
        return List.of(tool);
    }

    @Override
    public AgentMessage process(AgentMessage request) {
        try {
            Map<String, Object> args = new HashMap<>();
            String goal = String.valueOf(request.getContent().getOrDefault("goal", ""));
            String topic = String.valueOf(request.getContent().getOrDefault("topic", goal));
            // 阶段三：上游 PromptGenAgent 通过 resultMapping 注入的 prompt 可能是 Map
            String prompt = extractPromptText(request.getContent().get("prompt"));
            if (prompt.isBlank()) prompt = topic;
            String mediaType = String.valueOf(request.getContent().getOrDefault("mediaType", "image"));
            args.put("prompt", prompt.isBlank() ? goal : prompt);
            args.put("mediaType", mediaType);
            args.put("kpId", String.valueOf(request.getContent().getOrDefault("kpId", "kp_default")));

            AgentTool.ToolResult toolResult = tool.execute(args);
            if (!toolResult.success()) {
                return AgentMessage.error(request.getTaskId(), agentId, toolResult.content());
            }

            var node = mapper.readTree(toolResult.content());
            String mediaUrl = node.path("mediaUrl").asText("");
            boolean isVideo = "video".equalsIgnoreCase(node.path("mediaType").asText(mediaType));
            Map<String, Object> payload = new HashMap<>();
            payload.put("url", mediaUrl);
            payload.put("prompt", node.path("prompt").asText(prompt));
            payload.put("mediaType", isVideo ? "video" : "image");

            Map<String, Object> content = new HashMap<>();
            content.put("status", "completed");
            content.put("content", isVideo ? "视频已生成" : "图片已生成");
            content.put("artifactKind", isVideo ? "video" : "media_image");
            content.put("artifactPayload", payload);
            return AgentMessage.inform(request.getTaskId(), agentId, "pipeline", content);
        } catch (Exception e) {
            return AgentMessage.error(request.getTaskId(), agentId, "媒体生成失败: " + e.getMessage());
        }
    }

    /** 上游可能传入 String，也可能是 PipelineEngine resultMapping 注入的整个 Map */
    @SuppressWarnings("unchecked")
    private String extractPromptText(Object value) {
        if (value == null) return "";
        if (value instanceof String s) return s;
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> m = (Map<String, Object>) map;
            Object content = m.get("content");
            if (content instanceof String cs) return cs;
            if (content != null) return String.valueOf(content);
        }
        return String.valueOf(value);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        String goal = String.valueOf(request.getContent().getOrDefault("goal", ""));
        String topic = String.valueOf(request.getContent().getOrDefault("topic", goal));
        String prompt = String.valueOf(request.getContent().getOrDefault("prompt", topic));
        String mediaType = String.valueOf(request.getContent().getOrDefault("mediaType", "image"));
        return "请调用 generate_media 工具生成媒体内容。\n"
                + "媒体类型：" + mediaType + "\n"
                + "主题：" + topic + "\n"
                + "提示词：" + prompt + "\n"
                + "用户原始需求：" + goal;
    }

    @Override
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.MEDIA_GEN,
                "媒体生成",
                "生成图片或视频媒体内容（不包含流程图等代码图表）",
                List.of("media", "image", "video", "generate", "draw"),
                List.of(ToolSpec.of("generate_media", "生成媒体内容")),
                List.of("text"),
                List.of("media_image", "video"),
                1, 120000L
        );
    }
}
