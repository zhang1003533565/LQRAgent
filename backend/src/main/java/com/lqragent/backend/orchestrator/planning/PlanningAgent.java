package com.lqragent.backend.orchestrator.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineEngine;
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
    private final PipelineEngine pipelineEngine;
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
                "description", "用户请求生成学习资源，如讲义、代码示例、文档等。注意：单独请求生成题目、试题、练习题、测验题时应选择 route_quiz_generate。",
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
                "name", "route_quiz_generate",
                "description", "用户请求生成题目、试题、练习题、测验题、选择题、填空题、简答题、编程题；也包括基于知识库、资料、文档或上传内容出题。",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "出题主题或知识点"),
                        "count", Map.of("type", "integer", "description", "题目数量，可选"),
                        "difficulty", Map.of("type", "string", "description", "难度，可选"),
                        "questionTypes", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "description", "题型列表，可选"
                        )
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
                "description", "用户请求生成流程图、思维导图、架构图、UML图等可以用代码表现的图表或流程图",
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
                "name", "route_media_gen",
                "description", "用户请求生成真实的图片、示意图、照片、海报、绘画、插画等需要AI绘画生成的内容（不是流程图、思维导图等代码图表）",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "topic", Map.of("type", "string", "description", "图片/内容主题")
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
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_summary",
                "description", "用户请求总结知识点、生成复习摘要、提炼核心要点、生成学习笔记"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_assessment",
                "description", "用户请求评估答案质量、批改作业、评分、检查答案正确性"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_intervention",
                "description", "用户遇到学习困难或瓶颈，需要学习干预、学习建议、调整学习策略"
            )
        ),
        Map.of(
            "type", "function",
            "function", Map.of(
                "name", "route_clarify",
                "description", "用户表达学习意愿但信息不足以制定个性化计划，需要先询问更多细节。例如：'我想学python'、'教我编程'、'学习一下AI'等模糊请求。",
                "parameters", Map.of(
                    "type", "object",
                    "properties", Map.of(
                        "questions", Map.of(
                            "type", "array",
                            "items", Map.of("type", "string"),
                            "description", "需要询问用户的问题列表，2-3个关键问题"
                        ),
                        "context", Map.of(
                            "type", "string",
                            "description", "已识别的用户意图摘要"
                        )
                    ),
                    "required", List.of("questions", "context")
                )
            )
        )
    );
    
    private final AtomicInteger stepCounter = new AtomicInteger(0);
    
    /** 系统提示词（从文件加载） */
    private final String systemPrompt;

    public PlanningAgent(LlmClient llmClient, CapabilityRegistry capabilityRegistry, PipelineEngine pipelineEngine) {
        this.llmClient = llmClient;
        this.capabilityRegistry = capabilityRegistry;
        this.pipelineEngine = pipelineEngine;
        this.systemPrompt = loadPrompt("agents/planning/prompts/system.md");
    }

    /**
     * 核心方法：使用 Function Calling 识别用户意图
     *
     * @param message 用户原始消息
     * @param userId  用户ID
     * @return 规划结果
     */
    public PlanResult decompose(String message, String userId) {
        return decompose(message, userId, null);
    }

    /**
     * 核心方法：使用 Function Calling 识别用户意图（带对话历史）
     *
     * @param message 用户原始消息
     * @param userId  用户ID
     * @param chatHistory 对话历史（可选）
     * @return 规划结果
     */
    public PlanResult decompose(String message, String userId, String chatHistory) {
        log.info("[PlanningAgent] decomposing: {}", message);

        if (isExplicitMediaRequest(message)) {
            Map<String, Object> args = new HashMap<>();
            args.put("topic", message);
            args.put("prompt", message);
            args.put("mediaType", isVideoRequest(message) ? "video" : "image");
            return pipelineFor("media_gen", args, AgentIds.MEDIA_GEN, "generate_media");
        }

        try {
            // 构建消息列表，包含对话历史
            List<Map<String, Object>> messages = new ArrayList<>();
            
            // 添加对话历史（如果有）
            if (chatHistory != null && !chatHistory.isBlank()) {
                messages.add(Map.of("role", "user", "content", "以下是之前的对话历史，请参考上下文理解用户意图：\n" + chatHistory));
            }
            
            // 添加当前用户消息
            messages.add(Map.of("role", "user", "content", message));
            
            LlmClient.LlmResponse response = llmClient.chat(systemPrompt, messages, INTENT_TOOLS);
            
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

    private boolean isExplicitMediaRequest(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        boolean asksGenerate = m.contains("生成") || m.contains("画") || m.contains("做") || m.contains("create") || m.contains("generate");
        boolean asksVideoExplain = m.contains("用视频") || m.contains("视频解释") || m.contains("视频讲解")
                || m.contains("视频演示") || m.contains("动画解释") || m.contains("动画讲解");
        boolean asksScriptOnly = m.contains("视频脚本") || m.contains("分镜脚本") || m.contains("拍摄脚本") || m.contains("视频文案");
        boolean mediaWord = m.contains("一张") || m.contains("图片") || m.contains("图像") || m.contains("示意图") || m.contains("插画")
                || m.contains("海报") || m.contains("绘画") || m.contains("照片") || m.contains("动画")
                || m.contains("视频") || m.contains("真实示意图") || m.contains("image") || m.contains("video");
        boolean codeDiagramOnly = m.contains("流程图") || m.contains("思维导图") || m.contains("uml") || m.contains("mermaid") || m.contains("路线图");
        return (asksGenerate || asksVideoExplain) && mediaWord && !asksScriptOnly && !codeDiagramOnly;
    }

    private boolean isVideoRequest(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        return m.contains("视频") || m.contains("动画") || m.contains("video") || m.contains("movie");
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
                // === 业务意图：返回 PIPELINE，调用真正的 Agent ===
                case "route_learning_path" -> pipelineFor("learning_path", args, AgentIds.LEARNING_PATH, "generate_path");
                case "route_resource_generate" -> pipelineFor("resource", args, AgentIds.RESOURCE, "generate_lesson");
                case "route_quiz_generate" -> pipelineFor("quiz", args, AgentIds.QUIZ, "generate_quiz");
                case "route_diagram" -> pipelineFor("diagram", args, AgentIds.DIAGRAM, "generate_diagram");
                case "route_media_gen" -> pipelineFor("media_gen_direct", args, AgentIds.MEDIA_GEN, "generate_media");
                case "route_profile" -> pipelineFor("profile", args, AgentIds.PROFILE, "get_profile");
                case "route_recommendation" -> pipelineFor("recommendation", args, AgentIds.RECOMMENDATION, "recommend");
                case "route_summary" -> pipelineFor("summary", args, AgentIds.SUMMARY, "generate_summary");
                case "route_assessment" -> pipelineFor("assessment", args, AgentIds.ASSESSMENT, "grade");
                case "route_intervention" -> pipelineFor("intervention", args, AgentIds.INTERVENTION, "suggest_intervention");
                case "route_clarify" -> {
                    // 需求确认：返回 CLARIFY 类型，不执行 Pipeline
                    List<String> questions = args.containsKey("questions")
                            ? (List<String>) args.get("questions")
                            : List.of("你想学习哪个方向？", "你有相关基础吗？");
                    String context = args.containsKey("context")
                            ? (String) args.get("context")
                            : "用户想要学习";
                    yield PlanResult.clarify(questions, context);
                }
                // QA 保留 SIMPLE（走 QaAgent 单独处理，支持流式）
                case "route_qa" -> PlanResult.simple(PlanIntent.QA);
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
     * 构建单步 Pipeline（通过 PipelineEngine 调用 Agent）
     * 优先使用已注册的模板；否则创建单步 Pipeline
     */
    private PlanResult pipelineFor(String pipelineId, Map<String, Object> args, String agentId, String action) {
        // 优先使用已注册的模板
        PipelineConfig template = pipelineEngine.getTemplate(pipelineId);
        if (template != null) {
            log.info("[PlanningAgent] using registered template: {}", pipelineId);
            if (args == null || args.isEmpty()) {
                return PlanResult.pipeline(template, template.getSteps());
            }
            List<PipelineStep> steps = template.getSteps().stream().map(step -> {
                Map<String, Object> params = new HashMap<>();
                if (step.getParams() != null) params.putAll(step.getParams());
                params.putAll(args);
                return PipelineStep.builder()
                        .stepId(step.getStepId())
                        .agentId(step.getAgentId())
                        .action(step.getAction())
                        .params(params)
                        .dependsOn(step.getDependsOn())
                        .conditionType(step.getConditionType())
                        .maxRetries(step.getMaxRetries())
                        .timeoutMs(step.getTimeoutMs())
                        .optional(step.isOptional())
                        .resultMapping(step.getResultMapping())
                        .communicationMode(step.getCommunicationMode())
                        .build();
            }).toList();
            PipelineConfig config = PipelineConfig.builder()
                    .pipelineId(template.getPipelineId())
                    .name(template.getName())
                    .description(template.getDescription())
                    .steps(steps)
                    .totalTimeoutMs(template.getTotalTimeoutMs())
                    .parallel(template.isParallel())
                    .globalParams(template.getGlobalParams())
                    .build();
            return PlanResult.pipeline(config, steps);
        }

        // 回退：创建单步 Pipeline
        log.info("[PlanningAgent] creating single-step pipeline for: {} -> {}", pipelineId, agentId);
        PipelineStep step = PipelineStep.builder()
                .stepId(pipelineId)
                .agentId(agentId)
                .action(action)
                .params(new HashMap<>(args))
                .timeoutMs(60000)
                .maxRetries(1)
                .build();

        PipelineConfig config = PipelineConfig.builder()
                .pipelineId(pipelineId)
                .name(pipelineId)
                .description("Single-step: " + agentId)
                .steps(List.of(step))
                .totalTimeoutMs(120000)
                .build();

        return PlanResult.pipeline(config, List.of(step));
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
    
    /**
     * 从 classpath 加载提示词文件
     */
    private String loadPrompt(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("[PlanningAgent] failed to load prompt: {}", path, e);
            return "You are a helpful assistant.";
        }
    }
}
