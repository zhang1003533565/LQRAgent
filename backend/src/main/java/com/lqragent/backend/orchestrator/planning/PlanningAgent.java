package com.lqragent.backend.orchestrator.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.LlmClient;
import com.lqragent.backend.orchestrator.card.AgentCardRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * LLM 任务规划器 v4
 * <p>
 * 阶段二重写：从"意图分类器"升级为"任务规划器"
 * <ul>
 *   <li>删除 isExplicitMediaRequest / isVideoRequest 等硬编码关键词拦截</li>
 *   <li>删除 13 个 route_xxx 硬编码工具，改为 create_plan / route_simple / ask_clarify 三个工具</li>
 *   <li>基于 AgentCardRegistry 动态构建能力目录注入到提示词</li>
 *   <li>输出结构化 TaskPlan，由 OrchestratorCore 转为 PipelineConfig 执行</li>
 * </ul>
 */
@Slf4j
@Component
public class PlanningAgent {

    private final LlmClient llmClient;
    private final AgentCardRegistry cardRegistry;
    private final PlanPromptProvider promptProvider;

    public PlanningAgent(LlmClient llmClient,
                         AgentCardRegistry cardRegistry,
                         PlanPromptProvider promptProvider) {
        this.llmClient = llmClient;
        this.cardRegistry = cardRegistry;
        this.promptProvider = promptProvider;
    }

    public PlanResult decompose(String message, String userId) {
        return decompose(message, userId, null);
    }

    public PlanResult decompose(String message, String userId, String chatHistory) {
        return decompose(message, userId, chatHistory, null);
    }

    public PlanResult decompose(String message, String userId, String chatHistory, String learnerContext) {
        log.info("[PlanningAgent v4] decomposing: {}", message);

        // 1. 快通道：明确的问候/帮助（轻量正则，免去一次 LLM 调用）
        if (isGreeting(message)) {
            log.info("[PlanningAgent v4] fast-path GREETING");
            return PlanResult.simple(PlanIntent.GREETING);
        }
        if (isHelp(message)) {
            log.info("[PlanningAgent v4] fast-path HELP");
            return PlanResult.simple(PlanIntent.HELP);
        }

        // 2. LLM 规划
        try {
            String systemPrompt = promptProvider.buildSystemPrompt();
            List<Map<String, Object>> messages = promptProvider.buildUserMessages(
                    message, chatHistory, learnerContext);
            LlmClient.LlmResponse resp = llmClient.chat(systemPrompt, messages, PlanTools.all());

            if (!resp.isSuccess()) {
                log.warn("[PlanningAgent v4] LLM 调用失败: {}，兜底 QA", resp.error());
                return fallbackQa();
            }

            if (resp.toolCalls() == null || resp.toolCalls().isEmpty()) {
                log.warn("[PlanningAgent v4] LLM 未调用工具，兜底 QA。content={}", resp.content());
                return fallbackQa();
            }

            LlmClient.ToolCall tc = resp.toolCalls().get(0);
            return routeByToolCall(tc, message, userId);

        } catch (Exception e) {
            log.error("[PlanningAgent v4] decompose 失败: {}", e.getMessage(), e);
            return fallbackQa();
        }
    }

    private PlanResult routeByToolCall(LlmClient.ToolCall tc, String message, String userId) {
        String toolName = tc.name();
        Map<String, Object> args = tc.parseArguments();
        log.info("[PlanningAgent v4] toolCall: {} args={}", toolName, args);

        return switch (toolName) {
            case "route_simple" -> {
                // 阶段三关键兜底：原话是媒体请求时，不允许走 simple 单步问答，必须升级成完整媒体 plan
                if (isMediaIntent(message)) {
                    log.info("[PlanningAgent v4] route_simple 被覆盖：原话为媒体请求，升级为媒体 plan");
                    TaskPlan emptyPlan = TaskPlan.of(message, userId, new ArrayList<>());
                    TaskPlan upgradedPlan = ensureMediaSteps(emptyPlan, message, userId);
                    List<String> issues = validatePlan(upgradedPlan);
                    if (issues.isEmpty()) yield PlanResult.plan(upgradedPlan);
                }
                String intent = String.valueOf(args.getOrDefault("intent", "help"));
                yield PlanResult.simple(switch (intent) {
                    case "greeting" -> PlanIntent.GREETING;
                    case "help" -> PlanIntent.HELP;
                    default -> PlanIntent.QA;
                });
            }
            case "ask_clarify" -> {
                @SuppressWarnings("unchecked")
                List<String> questions = args.containsKey("questions")
                        ? (List<String>) args.get("questions")
                        : List.of("能否补充一下你的具体目标？");
                String context = String.valueOf(args.getOrDefault("context", "信息不足以制定计划"));
                yield PlanResult.clarify(questions, context);
            }
            case "create_plan" -> {
                TaskPlan plan = parsePlan(args, message, userId);
                if (plan == null) {
                    log.warn("[PlanningAgent v4] parsePlan 返回 null，兜底 QA");
                    yield fallbackQa();
                }
                // 阶段三修复：根据用户原话强制修正媒体类型，避免 LLM 漏传 mediaType=video
                enforceMediaType(plan, message);
                // 阶段三关键兜底：用户原话明确要求媒体（图/视频），但 LLM 没规划 media_gen 时强行补足
                plan = ensureMediaSteps(plan, message, userId);
                List<String> issues = validatePlan(plan);
                if (!issues.isEmpty()) {
                    log.warn("[PlanningAgent v4] plan 校验失败：{}，兜底 QA", issues);
                    yield fallbackQa();
                }
                yield PlanResult.plan(plan);
            }
            default -> {
                log.warn("[PlanningAgent v4] 未知工具: {}，兜底 QA", toolName);
                yield fallbackQa();
            }
        };
    }

    @SuppressWarnings("unchecked")
    private TaskPlan parsePlan(Map<String, Object> args, String fallbackGoal, String userId) {
        try {
            String planGoal = String.valueOf(args.getOrDefault("goal", fallbackGoal));
            Object stepsObj = args.get("steps");
            if (!(stepsObj instanceof List<?> rawSteps) || rawSteps.isEmpty()) {
                return null;
            }

            List<TaskStep> steps = new ArrayList<>();
            int idx = 0;
            for (Object o : rawSteps) {
                if (!(o instanceof Map<?, ?> map)) continue;
                idx++;
                Map<String, Object> sm = (Map<String, Object>) map;

                String stepId = String.valueOf(sm.getOrDefault("stepId", "s" + idx));
                String agentId = String.valueOf(sm.get("agentId"));
                String action = String.valueOf(sm.getOrDefault("action", "process"));

                Map<String, Object> params = sm.get("params") instanceof Map<?, ?> p
                        ? new HashMap<>((Map<String, Object>) p)
                        : new HashMap<>();
                // 把用户原始消息也带上，方便 Agent 理解上下文
                params.putIfAbsent("goal", fallbackGoal);

                List<String> dependsOn = sm.get("dependsOn") instanceof List<?> d
                        ? (List<String>) d
                        : List.of();

                String outputKind = sm.containsKey("outputKind") ? String.valueOf(sm.get("outputKind")) : null;

                steps.add(TaskStep.builder()
                        .stepId(stepId)
                        .agentId(agentId)
                        .action(action)
                        .params(params)
                        .dependsOn(dependsOn)
                        .outputKind(outputKind)
                        .maxRetries(2)
                        .optional(false)
                        .build());

                if (steps.size() >= 6) break;  // 硬截断，防止 LLM 失控
            }

            List<String> expectedOutputs = args.get("expectedOutputs") instanceof List<?> eo
                    ? (List<String>) eo
                    : List.of("text");

            return new TaskPlan(
                    "plan-" + System.currentTimeMillis(),
                    userId, planGoal, steps, expectedOutputs, "qa"
            );
        } catch (Exception e) {
            log.error("[PlanningAgent v4] parsePlan 异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验计划：所有 agentId 必须在 AgentCardRegistry 中存在
     * 防止 LLM 编造不存在的 agent 导致执行报错
     */
    private List<String> validatePlan(TaskPlan plan) {
        List<String> issues = new ArrayList<>();
        if (plan == null || plan.steps() == null || plan.steps().isEmpty()) {
            issues.add("空 plan");
            return issues;
        }
        for (TaskStep s : plan.steps()) {
            if (s.getAgentId() == null || s.getAgentId().isBlank()) {
                issues.add("step " + s.getStepId() + " 缺少 agentId");
                continue;
            }
            if (!cardRegistry.exists(s.getAgentId())) {
                issues.add("agentId 不存在: " + s.getAgentId());
            }
        }
        return issues;
    }

    /** LLM 失败兜底为单步 QA */
    private PlanResult fallbackQa() {
        return PlanResult.simple(PlanIntent.QA);
    }

    /** 阶段三：判断用户原话是否为媒体生成意图（视频或图片） */
    private boolean isMediaIntent(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        boolean wantsVideo = m.contains("视频") || m.contains("动画") || m.contains("video") || m.contains("animation");
        boolean wantsImage = m.contains("图片") || m.contains("图像") || m.contains("示意图")
                || m.contains("插画") || m.contains("海报") || m.contains("照片") || m.contains("画一") || m.contains("image");
        return wantsVideo || wantsImage;
    }

    /**
     * 阶段三关键兜底：用户原话明确要求"用视频/动画/图片解释"等，但 LLM 没规划 media_gen 时强行补足
     * <p>
     * 触发场景：LLM 把"用视频解释 X"误判成纯 QA，结果用户拿到一段"我用文字画给你看"的文本而非真视频。
     * 解决：服务端硬兜底——检测原话媒体关键词 + plan 缺 media_gen → 在末尾追加 prompt_gen + media_gen 步骤。
     */
    private TaskPlan ensureMediaSteps(TaskPlan plan, String message, String userId) {
        if (plan == null || message == null) return plan;
        String m = message.toLowerCase();
        boolean wantsVideo = m.contains("视频") || m.contains("动画") || m.contains("video") || m.contains("animation");
        boolean wantsImage = m.contains("图片") || m.contains("图像") || m.contains("示意图")
                || m.contains("插画") || m.contains("海报") || m.contains("照片") || m.contains("画一") || m.contains("image");
        if (!wantsVideo && !wantsImage) return plan;

        // 检查是否已有 media_gen 步骤
        boolean hasMediaGen = plan.steps() != null && plan.steps().stream()
                .anyMatch(s -> s.getAgentId() != null && s.getAgentId().contains("media_gen"));
        if (hasMediaGen) return plan;

        String mediaType = wantsVideo ? "video" : "image";
        String outputKind = wantsVideo ? "video" : "media_image";
        log.info("[PlanningAgent v4] ensureMediaSteps: LLM 漏规划媒体步骤，强行补 prompt_gen+media_gen mediaType={}", mediaType);

        // 在原 plan 末尾追加两步
        List<TaskStep> newSteps = new ArrayList<>(plan.steps() == null ? List.of() : plan.steps());
        // 找最后一个非 media 步骤的 stepId 作为依赖，没有就空依赖
        String lastStepId = newSteps.isEmpty() ? null : newSteps.get(newSteps.size() - 1).getStepId();

        Map<String, Object> promptParams = new HashMap<>();
        promptParams.put("mediaType", mediaType);
        promptParams.put("topic", message);
        TaskStep promptStep = TaskStep.builder()
                .stepId("auto_prompt_" + System.currentTimeMillis())
                .agentId("prompt_gen_agent")
                .action("generate")
                .params(promptParams)
                .dependsOn(lastStepId == null ? List.of() : List.of(lastStepId))
                .outputKind("text")
                .maxRetries(1)
                .build();

        Map<String, Object> mediaParams = new HashMap<>();
        mediaParams.put("mediaType", mediaType);
        TaskStep mediaStep = TaskStep.builder()
                .stepId("auto_media_" + System.currentTimeMillis())
                .agentId("media_gen_agent")
                .action("generate")
                .params(mediaParams)
                .dependsOn(List.of(promptStep.getStepId()))
                .outputKind(outputKind)
                .maxRetries(1)
                .build();

        newSteps.add(promptStep);
        newSteps.add(mediaStep);
        return new TaskPlan(plan.planId(), plan.userId(), plan.goal(), newSteps, plan.expectedOutputs(), plan.fallbackStrategy());
    }

    /**
     * 阶段三修复：基于用户原话强制修正媒体类型
     * <p>
     * 问题：LLM 在生成 plan 时常常漏传 mediaType，或者把"用视频解释"的请求填成 image
     * 解决：服务端兜底——只要原话出现 video/动画 类关键词，就把所有 media_gen / prompt_gen 步骤的 params.mediaType 强制设为 video
     */
    private void enforceMediaType(TaskPlan plan, String message) {
        if (plan == null || plan.steps() == null || message == null) return;
        String m = message.toLowerCase();
        boolean wantsVideo = m.contains("视频") || m.contains("动画") || m.contains("video") || m.contains("animation");
        boolean wantsImage = m.contains("图片") || m.contains("图像") || m.contains("示意图")
                || m.contains("插画") || m.contains("海报") || m.contains("照片") || m.contains("image");

        // 视频优先（用户说"用视频解释"时通常 wantsImage 为 false，但为防误判，video 优先）
        String enforcedType = null;
        if (wantsVideo) enforcedType = "video";
        else if (wantsImage) enforcedType = "image";
        if (enforcedType == null) return;

        for (TaskStep step : plan.steps()) {
            String aid = step.getAgentId();
            if (aid == null) continue;
            if (aid.contains("media_gen") || aid.contains("prompt_gen")) {
                Map<String, Object> params = step.getParams();
                if (params == null) {
                    params = new HashMap<>();
                    step.setParams(params);
                }
                Object existing = params.get("mediaType");
                if (existing == null || !enforcedType.equalsIgnoreCase(String.valueOf(existing))) {
                    params.put("mediaType", enforcedType);
                    log.info("[PlanningAgent v4] enforced mediaType={} on step {}", enforcedType, step.getStepId());
                }
            }
        }
    }

    private boolean isGreeting(String m) {
        if (m == null) return false;
        String s = m.trim().toLowerCase();
        if (s.length() > 12) return false;  // 太长大概率不是纯问候
        return s.matches("^(你好|您好|hi|hello|hey|嗨|在吗|在不在|早上好|晚上好|下午好)[!?。.！？]*$");
    }

    private boolean isHelp(String m) {
        if (m == null) return false;
        String s = m.trim().toLowerCase();
        if (s.length() > 30) return false;
        return s.contains("你能做什么")
                || s.contains("有什么功能")
                || s.contains("帮助")
                || s.equals("help")
                || s.equals("?")
                || s.equals("？");
    }
}
