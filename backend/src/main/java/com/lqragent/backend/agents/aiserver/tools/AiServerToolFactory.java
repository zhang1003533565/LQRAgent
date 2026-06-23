package com.lqragent.backend.agents.aiserver.tools;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentTool;
import com.lqragent.backend.chat.proxy.AiServerWsProxy;

import lombok.RequiredArgsConstructor;

/**
 * ai-server 工具工厂。
 */
@Component
@RequiredArgsConstructor
public class AiServerToolFactory {

    private final AiServerWsProxy proxy;

    public AgentTool deepSolveTool() {
        return new AiServerCapabilityTool(proxy, "deep_solve", "deep_solve",
                "多步推理求解复杂问题，适合需要拆解、证明、推理的长问题",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "question", Map.of("type", "string", "description", "问题"),
                                "context", Map.of("type", "string", "description", "上下文")
                        ),
                        "required", List.of("question")
                ));
    }

    public AgentTool deepQuestionTool() {
        return new AiServerCapabilityTool(proxy, "deep_question", "deep_question",
                "生成高质量题目，适合按主题、难度、薄弱点生成练习",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "topic", Map.of("type", "string", "description", "题目主题"),
                                "count", Map.of("type", "integer", "description", "题目数量"),
                                "difficulty", Map.of("type", "string", "description", "难度"),
                                "context", Map.of("type", "string", "description", "补充上下文")
                        ),
                        "required", List.of("topic")
                ));
    }

    public AgentTool visualizeTool() {
        return new AiServerCapabilityTool(proxy, "visualize", "visualize",
                "生成可视化内容，适合图表、结构图、流程图、动画说明",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "data", Map.of("type", "string", "description", "数据或概念描述"),
                                "type", Map.of("type", "string", "description", "可视化类型")
                        ),
                        "required", List.of("data")
                ));
    }
}
