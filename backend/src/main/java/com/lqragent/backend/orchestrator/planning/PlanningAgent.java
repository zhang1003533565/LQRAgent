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
                "description", "用户发送纯粹的问候（如你好、hi、hello、在吗），没有任何学习相关意图"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_help",
                "description", "用户询问系统功能、使用帮助、能做什么"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_learning_path",
                "description", "用户想学习某项技能或知识（如我想学Python、教我编程、零基础入门、学习计划、学习路线）",
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
                "description", "用户请求生成学习资源（如讲义、练习题、代码示例、知识点讲解）",
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
                "description", "用户提问或需要解答（如什么是X、X怎么用、X的原理、X和Y的区别）",
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
                "name", "route_diagram",
                "description", "用户请求生成图表、流程图、思维导图",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "图表主题")
                    ),
                    "required", List.of("topic")
                )
            )
        )
    );

    /** 系统提示词 */
    private static final String SYSTEM_PROMPT = """
        你是一个智能学习助手的任务路由器。
        根据用户消息，选择最合适的工具来处理：
        - 纯粹问候 → route_greeting
        - 询问功能/帮助 → route_help
        - 想学习某项技能/知识 → route_learning_path
        - 请求生成学习资源 → route_resource_generate
        - 提问/答疑 → route_qa
        - 请求生成图表 → route_diagram
        
        重要规则：
        1. 如果消息同时包含问候和学习意图（如"你好，我想学Python"），选择学习相关工具，不要选 greeting
        2. 只有纯粹的问候（如"你好"、"在吗"）才选 greeting
        3. "我想学"、"想学习"、"教我"、"入门"、"怎么学" 都表示学习意图
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
     * 降级：基于关键词的意图匹配
     */
    private PlanResult fallbackIntentMatch(String message) {
        String lower = message.toLowerCase();
        if (lower.matches("^(你好|hi|hello|嗨|您好).*") || lower.matches("^(你好|hi|hello|嗨|您好)")) {
            return PlanResult.simple(PlanIntent.GREETING);
        }
        if (lower.contains("帮助") || lower.contains("功能") || lower.contains("能做什么")) {
            return PlanResult.simple(PlanIntent.HELP);
        }

        // 使用 CapabilityRegistry 智能匹配（按标签/关键词）
        String matchedAgent = null;
        if (lower.contains("学") || lower.contains("路径") || lower.contains("计划") || lower.contains("规划")) {
            matchedAgent = capabilityRegistry.matchBestAgent("learning_path");
            return PlanResult.simple(matchedAgent.equals("qa_agent") ? PlanIntent.LEARNING_PATH : PlanIntent.LEARNING_PATH);
        }
        if (lower.contains("资源") || lower.contains("讲义") || lower.contains("材料") || lower.contains("资料")) {
            return PlanResult.simple(PlanIntent.RESOURCE);
        }
        if (lower.contains("题") || lower.contains("测验") || lower.contains("考试") || lower.contains("练习")) {
            return PlanResult.simple(PlanIntent.QUIZ);
        }
        if (lower.contains("图") || lower.contains("图表") || lower.contains("思维导图") || lower.contains("流程图")) {
            return PlanResult.simple(PlanIntent.DIAGRAM);
        }
        if (lower.contains("总结") || lower.contains("归纳") || lower.contains("概括")) {
            return PlanResult.simple(PlanIntent.SUMMARY);
        }
        if (lower.contains("推荐")) {
            return PlanResult.simple(PlanIntent.RECOMMENDATION);
        }
        if (lower.contains("评估") || lower.contains("批改") || lower.contains("打分")) {
            return PlanResult.simple(PlanIntent.ASSESSMENT);
        }
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
