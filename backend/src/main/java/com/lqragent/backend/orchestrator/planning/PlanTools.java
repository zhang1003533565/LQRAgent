package com.lqragent.backend.orchestrator.planning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 阶段二新增：PlanningAgent v2 的 Function Calling 工具定义
 * <p>
 * 替代旧的 13 个 route_xxx 硬编码工具，统一为三个顶层工具：
 * - create_plan：通用任务规划入口（覆盖原来 10+ 个 route_xxx 的所有场景）
 * - route_simple：处理问候/帮助等简单意图
 * - ask_clarify：信息不足时反问用户
 */
public class PlanTools {

    private PlanTools() {
    }

    /** 创建任务计划（通用入口） */
    public static Map<String, Object> createPlan() {
        Map<String, Object> stepProps = new LinkedHashMap<>();
        stepProps.put("stepId", Map.of("type", "string", "description", "唯一步骤ID，如 s1"));
        stepProps.put("agentId", Map.of("type", "string", "description", "Agent ID，必须来自能力目录"));
        stepProps.put("action", Map.of("type", "string", "description", "Agent 动作名"));
        stepProps.put("params", Map.of("type", "object", "description", "Agent 参数（可选）"));
        stepProps.put("dependsOn", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "依赖的前置步骤 ID 列表"
        ));
        stepProps.put("outputKind", Map.of("type", "string", "description", "期望产出 ArtifactKind（可选）"));

        Map<String, Object> stepSchema = new LinkedHashMap<>();
        stepSchema.put("type", "object");
        stepSchema.put("properties", stepProps);
        stepSchema.put("required", List.of("stepId", "agentId", "action"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("goal", Map.of("type", "string", "description", "本次任务总目标"));
        properties.put("steps", Map.of(
                "type", "array",
                "description", "1-6 个执行步骤",
                "items", stepSchema
        ));
        properties.put("expectedOutputs", Map.of(
                "type", "array",
                "items", Map.of("type", "string"),
                "description", "期望最终产出的 ArtifactKind 列表（可选）"
        ));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", List.of("goal", "steps"));

        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "create_plan",
                        "description", "为用户请求创建任务计划，组合 1-6 个 Agent 步骤协作完成。媒体/复合/普通任务都走这个工具。",
                        "parameters", parameters
                )
        );
    }

    /** 简单请求路由（问候 / 帮助） */
    public static Map<String, Object> routeSimple() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "route_simple",
                        "description", "处理纯粹的简单请求：问候、帮助。普通问答（如什么是X）请用 create_plan + qa_agent。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "intent", Map.of(
                                                "type", "string",
                                                "enum", List.of("greeting", "help"),
                                                "description", "意图：greeting=问候，help=询问功能"
                                        )
                                ),
                                "required", List.of("intent")
                        )
                )
        );
    }

    /** 反问澄清 */
    public static Map<String, Object> askClarify() {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", "ask_clarify",
                        "description", "信息严重不足无法制定计划，需向用户提问澄清。例如'我想学'但没说学什么。",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "questions", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string"),
                                                "description", "2-3 个关键澄清问题"
                                        ),
                                        "context", Map.of(
                                                "type", "string",
                                                "description", "已识别的用户意图摘要"
                                        )
                                ),
                                "required", List.of("questions", "context")
                        )
                )
        );
    }

    public static List<Map<String, Object>> all() {
        return List.of(createPlan(), routeSimple(), askClarify());
    }
}
