package com.lqragent.backend.agents;

import com.lqragent.backend.agent.Agent;
import com.lqragent.backend.agent.AgentBus;
import com.lqragent.backend.agent.AgentResult;
import com.lqragent.backend.agent.AgentTask;
import com.lqragent.backend.agent.QualityGate;
import com.lqragent.backend.orchestrator.dto.IntentResult;
import com.lqragent.backend.orchestrator.service.OrchestratorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 总调度智能体（Leader Agent）。
 * <p>
 * 职责：
 * 1. 意图识别（调用本地 OrchestratorService）
 * 2. 非 QA 路由：通过 AgentBus 派发到具体 agent → QualityGate 校验 → 返回结果
 * 3. QA 路由：返回 {route: "qa"}，由 ChatWebSocketHandler 走流式通道
 * </p>
 * <p>
 * 注入 ApplicationContext 而非 AgentBus 以避免构造期循环依赖
 * （AgentBus 持有所有 Agent 列表，包含本实例）。
 * </p>
 */
@Slf4j
@Component
public class OrchestratorAgent implements Agent {

    private final OrchestratorService orchestratorService;
    private final QualityGate qualityGate;
    private final ApplicationContext applicationContext;

    // 缓存，运行时一次性初始化
    private AgentBus agentBus;

    public OrchestratorAgent(OrchestratorService orchestratorService,
                             QualityGate qualityGate,
                             ApplicationContext applicationContext) {
        this.orchestratorService = orchestratorService;
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
    public String agentId() { return "orchestrator"; }

    @Override
    public AgentResult process(AgentTask task) {
        String message = (String) task.getPayload().getOrDefault("message", "");
        var intent = orchestratorService.determineIntent(message);
        String intentType = intent.getIntent();
        log.info("[OrchestratorAgent] intent={}, label={}, confidence={}", intentType, intent.getLabel(), intent.getConfidence());

        return switch (intentType) {
            case IntentResult.GREETING ->
                AgentResult.builder().success(true).data(Map.of(
                    "response", "你好！我是 LQRAgent 智能学习助手，可以帮你解答问题、规划学习路径、生成学习资源。请问有什么可以帮助你的？"
                )).build();

            case IntentResult.HELP ->
                AgentResult.builder().success(true).data(Map.of(
                    "response", """
                        我可以帮你做这些事情：
                        1. 📖 解答问题 — 发送任何 Python 相关问题
                        2. 🗺️ 规划学习路径 — 告诉我你想学什么
                        3. 📝 生成学习资源 — 包括讲义、题目、代码示例
                        4. 🎨 生成示意图

                        直接输入问题开始学习吧！"""
                )).build();

            case IntentResult.LEARNING_PATH -> {
                String goal = extractGoal(message);
                var sub = dispatchTo("learningpath", task, Map.of("goal", goal));
                if (!sub.isSuccess()) yield sub;
                yield AgentResult.builder().success(true).data(Map.of(
                    "response", "已为您规划学习路径：\n" + sub.getData().getOrDefault("goal", goal)
                )).build();
            }
            case IntentResult.RESOURCE_GENERATE -> {
                String kpId = extractKpId(message);
                var sub = dispatchTo("resourcefacade", task, Map.of("kpId", kpId, "resourceType", "LESSON"));
                if (!sub.isSuccess()) yield sub;
                yield AgentResult.builder().success(true).data(Map.of(
                    "response", "资源已生成。请查看您的学习资源。"
                )).build();
            }
            case IntentResult.MEDIA_GENERATE -> {
                String kpId = extractKpId(message);
                var sub = dispatchTo("resourcefacade", task, Map.of("kpId", kpId, "resourceType", "ILLUSTRATION"));
                if (!sub.isSuccess()) yield sub;
                yield AgentResult.builder().success(true).data(Map.of(
                    "response", "示意图已生成。"
                )).build();
            }
            // qa_question + unknown → 走流式通道
            default ->
                AgentResult.builder().success(true).data(Map.of(
                    "route", "qa",
                    "message", message
                )).build();
        };
    }

    /** 派发子任务并过质检闸门 */
    private AgentResult dispatchTo(String agentType, AgentTask original, Map<String, Object> extraPayload) {
        Map<String, Object> payload = new HashMap<>(original.getPayload());
        payload.putAll(extraPayload);
        payload.remove("message");

        AgentTask subTask = AgentTask.builder()
                .agentType(agentType)
                .userId(original.getUserId())
                .sessionId(original.getSessionId())
                .payload(payload)
                .build();

        AgentResult result = agentBus().dispatch(subTask).join();

        if (qualityGate.requiresGate(agentType)) {
            var gateResult = qualityGate.inspect(result);
            if (!gateResult.passed()) {
                log.warn("[OrchestratorAgent] QualityGate 拦截: agent={}, reason={}", agentType, gateResult.reason());
                return AgentResult.builder()
                        .success(false)
                        .errorMessage("内容校验未通过: " + gateResult.reason())
                        .build();
            }
        }

        return result;
    }

    private String extractGoal(String message) {
        String goal = message.replaceAll("^(?:我想|我要|帮我)?(?:学习|学|规划|看)", "").trim();
        if (goal.length() > 50) goal = goal.substring(0, 50);
        return goal.isBlank() ? "Python 高级编程" : goal;
    }

    private String extractKpId(String message) {
        return "";
    }
}
