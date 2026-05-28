package com.lqragent.backend.agent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体事件总线。
 * <p>
 * 所有 Agent 统一经过 {@link AgentEngine} 的 LLM 推理循环。
 * AgentEngine 内部自动检测 LLM 配置：未配置时降级到 {@link Agent#process(AgentTask)}。
 * </p>
 *
 * <h3>路由策略</h3>
 * 不再区分 Orchestrator / 内容 Agent，全部走 AgentEngine。
 * 路由决策由 LLM 在推理循环中自主完成（通过 System Prompt + Tool Calling）。
 *
 * <h3>并发隔离</h3>
 * AgentEngine 是 Prototype scope，每次 dispatch 获取新实例。
 * 多轮推理通过 SessionContext 按 sessionId 隔离。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentBus {

    private final List<Agent> agentList;
    private final ObjectFactory<AgentEngine> agentEngineFactory;
    private final Map<String, Agent> agentRegistry = new ConcurrentHashMap<>();

    /** 待完成任务追踪（用于异步场景） */
    private final Map<String, CompletableFuture<AgentResult>> pendingTasks = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        for (Agent agent : agentList) {
            String id = agent.agentId();
            if (agentRegistry.putIfAbsent(id, agent) != null) {
                log.warn("[AgentBus] 重复注册 agentId={}，跳过", id);
            } else {
                log.info("[AgentBus] 注册 agent: {}", id);
            }
        }
        log.info("[AgentBus] 已注册 {} 个智能体", agentRegistry.size());
    }

    /**
     * 派发任务给指定智能体。
     * <p>
     * 统一走 AgentEngine LLM 推理循环。
     * AgentEngine 内部自动检测 LLM 是否已配置：
     * <ul>
     *   <li>已配置 → 执行 Tool Calling 推理循环</li>
     *   <li>未配置 → 自动降级到 {@code agent.process(task)}</li>
     * </ul>
     * </p>
     */
    public CompletableFuture<AgentResult> dispatch(AgentTask task) {
        Agent agent = agentRegistry.get(task.getAgentType());
        if (agent == null) {
            return CompletableFuture.completedFuture(
                    AgentResult.builder()
                            .taskId(task.getTaskId())
                            .agentType(task.getAgentType())
                            .success(false)
                            .errorMessage("未知智能体: " + task.getAgentType())
                            .build()
            );
        }

        long start = System.currentTimeMillis();
        try {
            // 统一走 AgentEngine → 自动 fallback 到 process()
            AgentEngine engine = agentEngineFactory.getObject();
            String sessionId = task.getSessionId();
            AgentResult result = engine.run(agent, task, sessionId);

            result.setTaskId(task.getTaskId());
            result.setAgentType(task.getAgentType());
            result.setDurationMs(System.currentTimeMillis() - start);
            log.info("[AgentBus] {} 完成: taskId={}, {}, {}ms, engine={}",
                    task.getAgentType(), task.getTaskId(),
                    result.isSuccess() ? "success" : "fail",
                    result.getDurationMs(),
                    result.getData() != null && result.getData().containsKey("response") ? "agent-engine" : "process-fallback");
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("[AgentBus] {} 异常: taskId={}, error={}", task.getAgentType(), task.getTaskId(), e.getMessage(), e);
            return CompletableFuture.completedFuture(
                    AgentResult.builder()
                            .taskId(task.getTaskId())
                            .agentType(task.getAgentType())
                            .success(false)
                            .errorMessage(e.getMessage())
                            .durationMs(System.currentTimeMillis() - start)
                            .build()
            );
        }
    }

    /** 查询已注册的智能体列表 */
    public List<String> listAgents() {
        return List.copyOf(agentRegistry.keySet());
    }

    public int agentCount() {
        return agentRegistry.size();
    }
}
