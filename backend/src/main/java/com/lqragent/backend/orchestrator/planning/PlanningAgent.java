package com.lqragent.backend.orchestrator.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineStep;

import lombok.extern.slf4j.Slf4j;

/**
 * LLM 任务规划器 v3
 * 使用 Function Calling 让 LLM 自动选择意图和工具
 * <p>
 * 核心职责：
 * 1. 通过 Function Calling 识别用户意图
 * 2. 简单请求（问候/帮助/单点QA）→ 快速通道
 * 3. 复杂请求 → LLM 拆解为多步骤 PipelineConfig
 * <p>
 * v3 变更：使用 Function Calling 替代 prompt JSON 解析，更稳定可靠
 */
@Slf4j
@Component
public class PlanningAgent {

    private final LlmClient llmClient;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    /** Function Calling 工具定义：让 LLM 自动选择意图 */
    private static final List<Map<String, Object>> INTENT_TOOLS = List.of(
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_greeting",
                "description", "用户发送纯粹的问候，没有任何学习相关意图。只有完全的问候语才选这个。"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_help",
                "description", "用户明确询问这个系统能做什么、有哪些功能、如何使用。注意：询问具体内容（如知识库、画像）不算询问功能。"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_learning_path",
                "description", "用户想要学习某项技能或知识，需要制定学习计划或学习路线",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "goal", Map.of("type", "string", "description", "学习目标，如Python、机器学习")
                    ),
                    "required", List.of("goal")
                )
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_resource_generate",
                "description", "用户请求生成学习资源，如讲义、练习题、代码示例、文档等",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "资源主题")
                    ),
                    "required", List.of("topic")
                )
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_qa",
                "description", "用户提出具体问题需要解答，包括：知识概念查询、知识库内容查询、技术问题、原理问题等。这是最通用的路由，大部分请求都应该走这里。",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "question", Map.of("type", "string", "description", "用户的问题")
                    ),
                    "required", List.of("question")
                )
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_profile",
                "description", "用户想要查看自己的学习画像、学习统计、知识掌握度、学习偏好、学习记录等个人信息"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_diagram",
                "description", "用户请求生成图表、流程图、思维导图、架构图等可视化内容",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "图表主题")
                    ),
                    "required", List.of("topic")
                )
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_recommendation",
                "description", "用户请求推荐学习资源、练习题、学习内容"
            )
        )
    );

    /** 系统提示词 */
    private static final String SYSTEM_PROMPT = """
        你是一个智能学习助手的任务路由器。
        根据用户消息的语义和意图，选择最合适的工具来处理。
            
        核心原则：
        1. 理解用户的真实意图，而不是匹配关键词
        2. 只有纯粹的问候才选 greeting，包含任何学习意图的都不要选 greeting
        3. 询问具体内容（如"知识库有什么"、"我的学习情况"）不是询问功能，应该路由到对应功能
        4. 当不确定时，优先选择 route_qa（通用问答），因为它最灵活
        """;

    private final AtomicInteger stepCounter = new AtomicInteger(0);

    public PlanningAgent(LlmClient llmClient, CapabilityRegistry capabilityRegistry) {
        this.llmClient = llmClient;
        this.capabilityRegistry = capabilityRegistry;
    }

    /**
     * 核心方法：使用 Function Calling 识别用户意图
     *
     * @param message 用户原始消息
     * @param userId  用户ID
     * @return 规划结果
     */
    public PlanResult decompose(String message, String userId) {
        log.info("[PlanningAgent] decomposing: {}", message);

        try {
            // 使用 Function Calling 让 LLM 自动选择工具
            List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", message)
            );
            
            LlmClient.LlmResponse response = llmClient.chat(SYSTEM_PROMPT, messages, INTENT_TOOLS);
            
            // 检查是否有 tool_calls
            if (response.toolCalls() != null && !response.toolCalls().isEmpty()) {
                LlmClient.ToolCall toolCall = response.toolCalls().get(0);
                String toolName = toolCall.name();
                String argsJson = toolCall.argumentsJson();
                
                log.info("[PlanningAgent] Function Calling result: tool={}, args={}", toolName, argsJson);
                
                return mapToolCallToPlanResult(toolName, argsJson, message);
            }
            
            // 如果 LLM 没有调用工具，尝试从 content 解析
            String content = response.content();
            if (content != null && !content.isBlank()) {
                log.warn("[PlanningAgent] LLM returned content instead of tool_call: {}", content);
                return fallbackIntentMatch(message);
            }
            
            log.warn("[PlanningAgent] LLM returned empty, falling back to simple qa");
            return PlanResult.simple(PlanIntent.QA);

        } catch (Exception e) {
            log.error("[PlanningAgent] decompose failed: {}", e.getMessage(), e);
            return fallbackIntentMatch(message);
        }
    }

    /**
     * 将 Function Calling 结果映射到 PlanResult
     */
    private PlanResult mapToolCallToPlanResult(String toolName, String argsJson, String originalMessage) {
        try {
            Map<String, Object> args = argsJson != null && !argsJson.isBlank()
                ? mapper.readValue(argsJson, new TypeReference<>() {})
                : Map.of();
            
            return switch (toolName) {
                case "route_greeting" -> PlanResult.simple(PlanIntent.GREETING);
                case "route_help" -> PlanResult.simple(PlanIntent.HELP);
                case "route_learning_path" -> PlanResult.simple(PlanIntent.LEARNING_PATH);
                case "route_resource_generate" -> PlanResult.simple(PlanIntent.RESOURCE);
                case "route_qa" -> PlanResult.simple(PlanIntent.QA);
                case "route_diagram" -> PlanResult.simple(PlanIntent.DIAGRAM);
                case "route_profile" -> PlanResult.simple(PlanIntent.PROFILE);
                case "route_recommendation" -> PlanResult.simple(PlanIntent.RECOMMENDATION);
                default -> {
                    log.warn("[PlanningAgent] unknown tool: {}, falling back to qa", toolName);
                    yield PlanResult.simple(PlanIntent.QA);
                }
            };
        } catch (Exception e) {
            log.error("[PlanningAgent] failed to parse tool args: {}", e.getMessage());
            return PlanResult.simple(PlanIntent.QA);
        }
    }

    /**
     * 构建 PipelineConfig（复杂请求）
     */
    @SuppressWarnings("unchecked")
    private PlanResult buildPipelineConfig(Map<String, Object> parsed, String message) {
        String pipelineName = (String) parsed.getOrDefault("pipelineName", "dynamic_pipeline");
        List<Map<String, Object>> stepMaps = (List<Map<String, Object>>) parsed.get("steps");

        if (stepMaps == null || stepMaps.isEmpty()) {
            log.warn("[PlanningAgent] empty steps, falling back to qa");
            return PlanResult.simple(PlanIntent.QA);
        }

        List<PipelineStep> steps = new ArrayList<>();
        for (Map<String, Object> sm : stepMaps) {
            String stepId = (String) sm.getOrDefault("stepId", "step_" + stepCounter.incrementAndGet());
            String agent = (String) sm.get("agent");
            String action = (String) sm.getOrDefault("action", "process");
            List<String> dependsOn = sm.containsKey("dependsOn")
                    ? ((List<String>) sm.get("dependsOn"))
                    : List.of();

            Map<String, Object> params = sm.containsKey("params")
                    ? new HashMap<>((Map<String, Object>) sm.get("params"))
                    : new HashMap<>();

            // 注入用户原始消息作为上下文
            params.put("userMessage", message);
            params.put("context", message);

            // 设置合理的超时
            long timeoutMs = agent.contains("media_gen") ? 180_000 : 60_000;

            PipelineStep step = PipelineStep.builder()
                    .stepId(stepId)
                    .agentId(agent)
                    .action(action)
                    .params(params)
                    .dependsOn(dependsOn)
                    .timeoutMs(timeoutMs)
                    .maxRetries(1)
                    .build();

            steps.add(step);
        }

        PipelineConfig config = PipelineConfig.builder()
                .pipelineId("dynamic_" + System.currentTimeMillis())
                .name(pipelineName)
                .description("动态规划: " + truncate(message, 80))
                .steps(steps)
                .parallel(true)
                .totalTimeoutMs(300_000)
                .build();

        log.info("[PlanningAgent] created pipeline: {} steps, steps={}",
                steps.size(), steps.stream().map(PipelineStep::getStepId).toList());

        return PlanResult.pipeline(config, steps);
    }

    /**
     * 降级：当 LLM 不可用时，直接返回 QA（完全依赖 LLM 判断）
     */
    private PlanResult fallbackIntentMatch(String message) {
        log.warn("[PlanningAgent] LLM 意图识别失败，降级为 QA");
        return PlanResult.simple(PlanIntent.QA);
    }

    private PlanIntent parseIntent(String intent) {
        return switch (intent.toLowerCase()) {
            case "greeting" -> PlanIntent.GREETING;
            case "help" -> PlanIntent.HELP;
            case "learning_path" -> PlanIntent.LEARNING_PATH;
            case "resource" -> PlanIntent.RESOURCE;
            case "quiz" -> PlanIntent.QUIZ;
            case "diagram" -> PlanIntent.DIAGRAM;
            case "summary" -> PlanIntent.SUMMARY;
            case "recommendation" -> PlanIntent.RECOMMENDATION;
            case "assessment" -> PlanIntent.ASSESSMENT;
            default -> PlanIntent.QA;
        };
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
