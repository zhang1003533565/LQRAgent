package com.lqragent.backend.core.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Agent 协作链路编排器。
 * <p>
 * 支持声明式多 Agent 串联：
 * <pre>
 *   AgentPipeline.of("learning-path-full")
 *       .then(AgentIds.LEARNING_PATH, ctx -> Map.of("goal", goal))
 *       .then(AgentIds.RESOURCE_GENERATION, (prev, ctx) -> Map.of("kpId", extractKpId(prev)))
 *       .execute(userId, sessionId);
 * </pre>
 * 每一步的输出作为下一步的上下文输入。任何步骤失败则整条链路终止。
 * </p>
 */
@Slf4j
public class AgentPipeline {

    private final String name;
    private final List<PipelineStep> steps = new ArrayList<>();
    private final AgentBus agentBus;

    private AgentPipeline(String name, AgentBus agentBus) {
        this.name = name;
        this.agentBus = agentBus;
    }

    public static AgentPipeline of(String name, AgentBus agentBus) {
        return new AgentPipeline(name, agentBus);
    }

    /**
     * 添加一步：固定输入，不依赖前一步结果。
     */
    public AgentPipeline then(String agentType, Function<Map<String, Object>, Map<String, Object>> inputBuilder) {
        steps.add(new PipelineStep(agentType, (prev, ctx) -> inputBuilder.apply(ctx)));
        return this;
    }

    /**
     * 添加一步：输入依赖前一步的输出。
     */
    public AgentPipeline then(String agentType,
                               BiFunction<Map<String, Object>, Map<String, Object>, Map<String, Object>> inputBuilder) {
        steps.add(new PipelineStep(agentType, inputBuilder));
        return this;
    }

    /**
     * 执行整条链路。
     *
     * @return 最后一步的 AgentResult，任何步骤失败则返回该步骤的错误结果
     */
    public AgentResult execute(Long userId, String sessionId) {
        log.info("[Pipeline] 开始执行: name={}, steps={}", name, steps.size());
        Map<String, Object> context = new java.util.LinkedHashMap<>();

        for (int i = 0; i < steps.size(); i++) {
            PipelineStep step = steps.get(i);
            Map<String, Object> input = step.inputBuilder.apply(
                    i > 0 ? steps.get(i - 1).lastResult != null ? steps.get(i - 1).lastResult.getData() : Map.of() : Map.of(),
                    context);

            log.info("[Pipeline] 步骤 {}/{}: agent={}, inputKeys={}", i + 1, steps.size(), step.agentType, input.keySet());

            AgentTask task = AgentTask.builder()
                    .agentType(step.agentType)
                    .userId(userId)
                    .sessionId(sessionId != null ? sessionId + ":pipeline:" + name : null)
                    .payload(input)
                    .build();

            AgentResult result = agentBus.dispatch(task).join();
            step.lastResult = result;

            if (!result.isSuccess()) {
                log.warn("[Pipeline] 步骤失败: name={}, step={}/{}, agent={}, error={}",
                        name, i + 1, steps.size(), step.agentType, result.getErrorMessage());
                return result;
            }

            // 将结果数据注入上下文，供后续步骤使用
            if (result.getData() != null) {
                context.putAll(result.getData());
            }
            context.put("_lastAgent", step.agentType);
            context.put("_lastSuccess", true);
        }

        log.info("[Pipeline] 执行完成: name={}", name);
        return steps.get(steps.size() - 1).lastResult;
    }

    private static class PipelineStep {
        final String agentType;
        final BiFunction<Map<String, Object>, Map<String, Object>, Map<String, Object>> inputBuilder;
        AgentResult lastResult;

        PipelineStep(String agentType,
                     BiFunction<Map<String, Object>, Map<String, Object>, Map<String, Object>> inputBuilder) {
            this.agentType = agentType;
            this.inputBuilder = inputBuilder;
        }
    }
}
