package com.lqragent.backend.agent;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体事件总线（Phase 1：进程内同步分发）。
 * Orchestrator 通过此总线派发任务给目标 Agent。
 * Phase 2 可替换为 Redis Streams 实现，接口不变。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentBus {

    private final List<Agent> agentList;
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
     * 同步执行，返回 CompletableFuture 便于 Orchestrator 编排。
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
            AgentResult result = agent.process(task);
            result.setTaskId(task.getTaskId());
            result.setAgentType(task.getAgentType());
            result.setDurationMs(System.currentTimeMillis() - start);
            log.info("[AgentBus] {} 完成: taskId={}, success={}, {}ms",
                    task.getAgentType(), task.getTaskId(), result.isSuccess(), result.getDurationMs());
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("[AgentBus] {} 异常: taskId={}, error={}", task.getAgentType(), task.getTaskId(), e.getMessage());
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
