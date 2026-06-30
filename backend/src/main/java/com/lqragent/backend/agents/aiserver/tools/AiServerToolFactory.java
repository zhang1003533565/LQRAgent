package com.lqragent.backend.agents.aiserver.tools;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.chat.proxy.AiServerWsProxy;

import lombok.RequiredArgsConstructor;

/**
 * ai-server capability 工具工厂。
 * 为管理后台 / 控制台 / 测试接口提供预封装的 ai-server 能力工具。
 */
@Component
@RequiredArgsConstructor
public class AiServerToolFactory {

    private final AiServerWsProxy aiServerWsProxy;

    public AiServerCapabilityTool deepSolveTool() {
        return new AiServerCapabilityTool(
                aiServerWsProxy,
                "deep_solve",
                "deep_solve",
                "深度解题：对复杂编程/数学问题进行分步推理与解答",
                Map.of()
        );
    }

    public AiServerCapabilityTool deepQuestionTool() {
        return new AiServerCapabilityTool(
                aiServerWsProxy,
                "deep_question",
                "deep_question",
                "深度出题：根据知识点与难度要求生成练习题",
                Map.of()
        );
    }

    public AiServerCapabilityTool visualizeTool() {
        return new AiServerCapabilityTool(
                aiServerWsProxy,
                "visualize",
                "visualize",
                "可视化生成：将知识点转化为图表、流程图或示意图",
                Map.of()
        );
    }
}
