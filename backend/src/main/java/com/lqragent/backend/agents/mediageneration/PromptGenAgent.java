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
        // 教学向媒体 Prompt：说明用途即可，布局交给生图模型自由发挥
        return """
                你是媒体生成 Prompt 工程师，只产出可直接喂给图片/视频生成模型的 Prompt。

                硬性约束：
                1. 严禁输出分镜表、脚本表格、视频脚本、台词、旁白
                2. 严禁输出 markdown 标题、列表、表格
                3. 必须直接输出最终 Prompt 文本（一段话），英文描述风格与构图，中文描述教学场景与知识点
                4. 图片 Prompt：面向教学场景，风格清晰易懂，突出要讲解的概念
                5. 视频 Prompt：3-15 秒短片、明确镜头与主体动作
                6. Prompt 长度 50-200 字
                7. 不要包含「以下是 Prompt:」等前后缀，直接输出 Prompt 本体

                图片构图（重要，用正面描述，不要堆砌 no xxx 负面词）：
                - 默认「单幅统一画面」：one cohesive scene / single unified illustration，一个主体或一个场景
                - 用户要装饰/渲染/插画/海报/艺术图 → 强调 decorative render / artistic illustration，避免 infographic、icon grid、storyboard
                - 用户要示意图且含代码/流程/对比 → 用「中心构图 + 少量标注」的单场景，不要多个独立方块排成矩阵
                - 禁止在 Prompt 末尾追加 no nine-grid、no multi-panel 等一长串否定词（生图模型往往忽略）
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
            sb.append("请输出一段直接可用的图片生成 Prompt，面向教学场景，布局与风格可自由发挥。");
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
                "为图片/视频媒体生成教学向提示词",
                List.of("prompt", "media_prompt", "prompt_engineering"),
                List.of(),
                List.of("text"),
                List.of("text"),
                1, 30000L
        );
    }
}
