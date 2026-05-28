package com.lqragent.backend.agents.orchestrator;

import com.lqragent.backend.framework.Agent;
import com.lqragent.backend.framework.AgentIds;
import com.lqragent.backend.framework.AgentBus;
import com.lqragent.backend.framework.AgentResult;
import com.lqragent.backend.framework.AgentTask;
import com.lqragent.backend.framework.QualityGate;
import com.lqragent.backend.framework.RequestContext;
import com.lqragent.backend.framework.ToolRegistry;
import com.lqragent.backend.framework.ToolSchema;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 总调度智能体（Leader Agent）。
 * <p>
 * === 重构说明（Phase B）===
 *
 * <h3>1. 剥离硬编码</h3>
 * process() 不再包含 switch/case 意图分发。
 * LLM 通过 AgentEngine 推理循环自主决策。
 *
 * <h3>2. System Prompt 升级</h3>
 * 提示词明确要求 LLM 作为"最高决策调度大脑"，
 * 根据上下文自主决定工具调用顺序，并确认上一步成功后再走下一步。
 *
 * <h3>3. 斩断双重执行</h3>
 * 工具执行器不再提前调用 Service，只负责派发子任务。
 * 所有业务动作严格收拢在子 Agent 的 ToolRegistry 触发后的 Service 调用中。
 * </p>
 */
@Slf4j
@Component
public class OrchestratorAgent implements Agent {

    private final QualityGate qualityGate;
    private final ApplicationContext applicationContext;

    private AgentBus agentBus;

    public OrchestratorAgent(QualityGate qualityGate,
                             ApplicationContext applicationContext) {
        this.qualityGate = qualityGate;
        this.applicationContext = applicationContext;
    }

    private AgentBus agentBus() {
        if (agentBus == null) {
            agentBus = applicationContext.getBean(AgentBus.class);
        }
        return agentBus;
    }

    @Override
    public String agentId() { return AgentIds.ORCHESTRATOR; }

    // ================================================================
    //  [重构] process() 不再包含 switch/case 硬编码分发
    //  只作为 LLM 未配置时的极简降级入口
    // ================================================================
    @Override
    public AgentResult process(AgentTask task) {
        Long userId = RequestContext.getUserId();
        String requestId = RequestContext.getRequestId();
        String message = (String) task.getPayload().getOrDefault("message", "");
        log.info("[Orchestrator] 降级模式: requestId={}, userId={}, msg={}", requestId, userId, message);

        // 极简降级：请用户先配置 LLM
        return AgentResult.builder()
                .success(true)
                .data(Map.of(
                    "response", "您好！当前系统尚未配置大模型 API Key。请在管理后台「系统管理 → 模型配置」中配置 LLM 后使用智能调度功能。"
                ))
                .build();
    }

    // ================================================================
    //  [重构] System Prompt — 最高决策调度大脑
    //  LLM 必须自主分析意图、决策工具调用顺序、确认上一步状态后再走下一步
    // ================================================================
    @Override
    public String getSystemPrompt(AgentTask task) {
        return """
            你是 LQRAgent 的最高决策调度大脑。你的核心职责是：

            ## 决策原则
            1. **自主分析意图**：仔细阅读用户消息，理解用户真正想要什么（问候、帮助、学习路径、资源生成、示意图）
            2. **按需调用工具**：不要一次性调用所有工具。先调最需要的那个，确认成功后（收到 JSON 状态码）再决策下一步
            3. **关注上下文**：如果上一轮已经生成了路径，这一轮用户要求资源，不要重复生成路径
            4. **拒绝并行派发**：一次只调一个工具。等待该工具返回成功状态后再决策下一个

            ## 可用工具及调用规则

            ### greet — 场景：用户打招呼
            用户说"你好/您好/hi"时调用。返回欢迎语后即结束，不要继续调其他工具。

            ### handle_help — 场景：用户询问功能
            用户问"你能做什么/有什么功能"时调用。返回帮助信息后即结束。

            ### handle_learning_path — 场景：用户要学习路径
            用户说"我想学XX/怎么学XX/学习路线"时调用。
            **调用后必须检查返回的 status**，确认路径生成成功后再问用户是否需要生成配套资源。

            ### handle_resource_generate — 场景：用户要学习资源
            用户说"生成讲义/出题/给我讲XX/生成资源"时调用。
            此工具内部会调度教学资源专家自动分析学生画像，按需生成最合适的资源类型。
            **调用后检查返回的 status**，确认资源生成成功后，可询问用户是否需要其他类型资源。

            ### handle_media_generate — 场景：用户要示意图
            用户说"画图/示意图/流程图"时调用。

            ## 输出规范
            - 工具返回的是 JSON 状态码（如 {"status":"success","resourceId":128}），不是完整内容
            - 根据状态码判断成功/失败，不要假设工具结果内容
            - 所有子任务结果汇总后，用自然语言回复用户
            - 未知意图或无法处理的问题，告诉用户你能做什么
            """;
    }

    @Override
    public List<ToolSchema> getTools() {
        return List.of(
            ToolSchema.of("greet", "用户打招呼时调用，返回欢迎语。调用后本轮结束。"),
            ToolSchema.of("handle_help", "用户询问功能时调用，返回帮助说明。调用后本轮结束。"),
            ToolSchema.of("handle_learning_path", "用户要规划学习路径时调用（如「我想学Python装饰器」）。内部调度 LearningPathAgent 生成路径。",
                ToolSchema.params(Map.of("message", ToolSchema.stringParam("用户消息", "如：我想学Python装饰器")), "message")),
            ToolSchema.of("handle_resource_generate", "用户要生成学习资源时调用（如「生成讲义/出题/给我讲讲装饰器」）。内部调度 ResourceGenerationAgent 按学生画像按需生成。",
                ToolSchema.params(Map.of("message", ToolSchema.stringParam("用户消息", "如：给我生成Python装饰器的学习资源")), "message")),
            ToolSchema.of("handle_media_generate", "用户要生成示意图时调用（如「画一个流程图」）。",
                ToolSchema.params(Map.of("message", ToolSchema.stringParam("用户消息", "如：画一个装饰器的流程图")), "message"))
        );
    }

    // ================================================================
    //  [重构] 工具执行器 — 只做子任务派发，不提前调 Service
    //  所有执行逻辑收拢在子 Agent 的 ToolRegistry 触发后的 Service 调用中
    // ================================================================
    @Override
    public void registerTools(ToolRegistry registry) {
        // ——— greet：返回欢迎语，不做任何子任务 ———
        registry.register(agentId(), "greet", args -> {
            return Map.of(
                "status", "success",
                "response", "你好！我是 LQRAgent 智能学习助手，可以帮你解答问题、规划学习路径、生成学习资源。请问有什么可以帮助你的？"
            );
        });

        // ——— handle_help：返回帮助说明 ———
        registry.register(agentId(), "handle_help", args -> {
            return Map.of(
                "status", "success",
                "response", """
                    我可以帮你做这些事情：
                    1. 📖 解答问题 — 发送任何 Python 相关问题
                    2. 🗺️ 规划学习路径 — 告诉我你想学什么
                    3. 📝 生成学习资源 — 包括讲义、题目、代码示例
                    4. 🎨 生成示意图

                    直接输入问题开始学习吧！"""
            );
        });

        // ——— handle_learning_path：派发子任务到 LearningPathAgent ———
        registry.register(agentId(), "handle_learning_path", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            String msg = (String) p.getOrDefault("message", "");
            Long userId = RequestContext.getUserId();
            String sessionId = (String) p.get("sessionId");

            // [重构] 只做子任务派发，不提前调 Service
            // LearningPathAgent 的 AgentEngine 会自己决定怎么算路径
            AgentResult subResult = dispatchToAgent(AgentIds.LEARNING_PATH, userId, sessionId,
                    Map.of("message", msg));

            if (subResult == null || !subResult.isSuccess()) {
                // 使用 LinkedHashMap 避免 null errorMessage 引发 Map.of NPE
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("status", "error");
                err.put("error", "路径规划失败");
                if (subResult != null && subResult.getErrorMessage() != null) {
                    err.put("detail", subResult.getErrorMessage());
                }
                return err;
            }

            // 只返回状态摘要，不返回完整路径内容（截断由 AgentEngine 保障）
            return Map.of(
                "status", "success",
                "message", "学习路径已规划完成",
                "sub_agent", AgentIds.LEARNING_PATH
            );
        });

        // ——— handle_resource_generate：派发子任务到 ResourceGenerationAgent ———
        registry.register(agentId(), "handle_resource_generate", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            String msg = (String) p.getOrDefault("message", "");
            Long userId = RequestContext.getUserId();
            String sessionId = (String) p.get("sessionId");

            // [重构] 只做子任务派发，不提前调 Service
            // ResourceGenerationAgent 的 AgentEngine 会自主分析学生画像并按需生成资源
            AgentResult subResult = dispatchToAgent(AgentIds.RESOURCE_GENERATION, userId, sessionId,
                    Map.of("message", msg));

            if (subResult == null || !subResult.isSuccess()) {
                java.util.Map<String, Object> err = new java.util.LinkedHashMap<>();
                err.put("status", "error");
                err.put("error", "资源生成失败");
                if (subResult != null && subResult.getErrorMessage() != null) {
                    err.put("detail", subResult.getErrorMessage());
                }
                return err;
            }

            return Map.of(
                "status", "success",
                "message", "资源已由教学资源专家生成并存入数据库，质检流程已自动触发",
                "sub_agent", AgentIds.RESOURCE_GENERATION,
                "next_step_hint", "你可以询问用户是否需要其他类型的资源，或继续解答问题"
            );
        });

        // ——— handle_media_generate：派发子任务到 MediaGenerationAgent ———
        registry.register(agentId(), "handle_media_generate", args -> {
            Map<String, Object> p = registry.parseArgs(args);
            String msg = (String) p.getOrDefault("message", "");
            Long userId = RequestContext.getUserId();
            String sessionId = (String) p.get("sessionId");

            AgentResult subResult = dispatchToAgent(AgentIds.MEDIA_GENERATION, userId, sessionId,
                    Map.of("message", msg));

            if (subResult == null || !subResult.isSuccess()) {
                return Map.of(
                    "status", "error",
                    "error", "示意图生成失败"
                );
            }

            return Map.of(
                "status", "success",
                "message", "示意图已生成",
                "sub_agent", AgentIds.MEDIA_GENERATION
            );
        });
    }

    // ==================== 子任务派发工具 ====================

    /**
     * [重构] 统一的子任务派发入口。
     * 只创建子任务并投递到 AgentBus，不提前调任何 Service。
     * 子 Agent 的 AgentEngine 推理循环会自行决策工具调用。
     * <p>
     * <b>上下文隔离</b>：子 sessionId 使用独立的后缀，
     * 避免子 Agent 的 SessionContext 污染父 Agent 的消息序列。
     * </p>
     * <b>重试</b>：子 Agent 失败时自动重试 1 次。
     */
    private AgentResult dispatchToAgent(String agentType, Long userId, String sessionId,
                                         Map<String, Object> extraPayload) {
        String subSessionId = (sessionId != null) ? sessionId + ":sub:" + agentType : null;
        Map<String, Object> payload = new HashMap<>(extraPayload);

        AgentResult result = null;
        for (int attempt = 0; attempt <= 1; attempt++) {
            AgentTask subTask = AgentTask.builder()
                    .agentType(agentType)
                    .userId(userId)
                    .sessionId(subSessionId)
                    .payload(payload)
                    .build();

            result = agentBus().dispatch(subTask).join();

            if (result.isSuccess()) break;
            if (attempt == 0) {
                log.warn("[Orchestrator] 子Agent失败，重试: agent={}, error={}", agentType, result.getErrorMessage());
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }

        // 质检闸门
        if (result != null && qualityGate.requiresGate(agentType)) {
            var gateResult = qualityGate.inspect(result);
            if (!gateResult.passed()) {
                log.warn("[Orchestrator] QualityGate 拦截: agent={}, reason={}", agentType, gateResult.reason());
                return AgentResult.builder()
                        .success(false)
                        .errorMessage("内容校验未通过: " + gateResult.reason())
                        .build();
            }
        }
        return result;
    }
}
