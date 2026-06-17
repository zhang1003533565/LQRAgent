package com.lqragent.backend.agents.mediageneration;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.agents.base.AgentToolRegistry;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.agents.BaseAgent;
import com.lqragent.backend.orchestrator.card.AgentCard;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.prompt.service.PromptService;

@Component
public class PromptGenAgent extends BaseAgent {

    public PromptGenAgent(RedisStreamsService streams, LlmClient llmClient,
                           AgentToolRegistry toolRegistry, PromptService promptService) {
        super(AgentIds.PROMPT_GEN, streams, llmClient, toolRegistry, promptService);
    }

    @Override
    protected String getSystemPrompt() {
        // 阶段三：硬约束系统提示词，避免 LLM 输出脚本/九宫格
        return """
                你是媒体生成 Prompt 工程师，只产出可直接喂给图片/视频生成模型的 Prompt。

                硬性约束：
                1. 严禁输出任何分镜表、脚本表格、视频脚本、台词、旁白
                2. 严禁输出 markdown 标题、列表、表格
                3. 必须直接输出最终 Prompt 文本（一段话），用英文表达视觉风格关键词，用中文描述场景内容
                4. 图片 Prompt：单一连贯场景、中心构图、扁平现代教育插画风格、清晰视觉层次
                   必须显式追加：no nine-grid, no multi-panel layout, no collage, no comic panels, no storyboard, no split screen
                5. 视频 Prompt：3-15 秒短片、明确镜头与主体动作，禁止"分镜""脚本""旁白"等词
                6. Prompt 长度 50-200 字
                7. 不要包含解释性的"以下是 Prompt:"等前后缀，直接输出 Prompt 本体
                """;
    }

    @Override
    protected List<AgentTool> getTools() { return List.of(); }

    @Override
    public AgentMessage process(AgentMessage request) {
        return executeLlmLoop(request);
    }

    @Override
    protected String buildUserMessage(AgentMessage request) {
        Map<String, Object> ctx = request.getContent();
        String mediaType = String.valueOf(ctx.getOrDefault("mediaType", "image"));
        // 阶段三：上游 QA 步骤的结果是 Map，需从中提取 content 字段
        String explanation = extractText(ctx.get("explanation"));
        if (explanation.isBlank()) explanation = extractText(ctx.get("content"));
        String goal = String.valueOf(ctx.getOrDefault("goal", ""));
        String topic = String.valueOf(ctx.getOrDefault("topic", goal));

        StringBuilder sb = new StringBuilder();
        sb.append("媒体类型：").append(mediaType).append("\n");
        sb.append("主题：").append(topic).append("\n");
        if (!explanation.isBlank()) {
            // 截断过长的讲解文本，避免 prompt 太长
            String trimmed = explanation.length() > 800 ? explanation.substring(0, 800) + "..." : explanation;
            sb.append("讲解上下文：\n").append(trimmed).append("\n");
        }
        sb.append("用户原始需求：").append(goal).append("\n\n");
        if ("video".equalsIgnoreCase(mediaType)) {
            sb.append("请输出一段直接可用的视频生成 Prompt（3-15 秒短片，单镜头/主体动作描述），禁止任何脚本表格或台词。");
        } else {
            sb.append("请输出一段直接可用的图片生成 Prompt，禁止九宫格/多面板布局。");
        }
        return sb.toString();
    }

    /** 上游可能是 String 也可能是 Map（PipelineEngine resultMapping 注入的整个结果对象） */
    @SuppressWarnings("unchecked")
    private String extractText(Object value) {
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
    public AgentCard getAgentCard() {
        return new AgentCard(
                AgentIds.PROMPT_GEN,
                "媒体 Prompt 生成",
                "为图片/视频媒体生成标准化提示词，避免九宫格等不符合预期布局",
                List.of("prompt", "media_prompt", "prompt_engineering"),
                List.of(),
                List.of("text"),
                List.of("text"),
                1, 30000L
        );
    }
}
