package com.lqragent.backend.framework;

import com.lqragent.backend.chat.entity.AgentRunLog;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
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

    /** 最近 100 条执行记录 */
    private final Deque<AgentRunLog> runLogs = new ArrayDeque<>();
    private static final int MAX_RUN_LOGS = 100;
    /** 网络类错误重试次数 */
    private static final int MAX_RETRIES = 1;
    private static final long RETRY_DELAY_MS = 1000;

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
            AgentResult err = AgentResult.builder()
                    .taskId(task.getTaskId())
                    .agentType(task.getAgentType())
                    .success(false)
                    .errorMessage("未知智能体: " + task.getAgentType())
                    .build();
            addRunLog(task, err, 0);
            return CompletableFuture.completedFuture(err);
        }

        long start = System.currentTimeMillis();
        Exception lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                AgentEngine engine = agentEngineFactory.getObject();
                String sessionId = task.getSessionId();
                AgentResult result = engine.run(agent, task, sessionId);

                result.setTaskId(task.getTaskId());
                result.setAgentType(task.getAgentType());
                result.setDurationMs(System.currentTimeMillis() - start);
                log.info("[AgentBus] {} 完成: taskId={}, {}, {}ms, attempt={}",
                        task.getAgentType(), task.getTaskId(),
                        result.isSuccess() ? "success" : "fail",
                        result.getDurationMs(), attempt);
                addRunLog(task, result, attempt);
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                lastException = e;
                boolean isNetworkError = isNetworkError(e);
                if (attempt < MAX_RETRIES && isNetworkError) {
                    log.warn("[AgentBus] 网络错误，重试 {}/{}: agent={}, error={}",
                            attempt + 1, MAX_RETRIES, task.getAgentType(), e.getMessage());
                    try { Thread.sleep(RETRY_DELAY_MS * (attempt + 1)); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                } else {
                    break;
                }
            }
        }

        log.error("[AgentBus] {} 异常: taskId={}, error={}", task.getAgentType(), task.getTaskId(),
                lastException != null ? lastException.getMessage() : "unknown");
        AgentResult failResult = AgentResult.builder()
                .taskId(task.getTaskId())
                .agentType(task.getAgentType())
                .success(false)
                .errorMessage(lastException != null ? lastException.getMessage() : "unknown")
                .durationMs(System.currentTimeMillis() - start)
                .build();
        addRunLog(task, failResult, 0);
        return CompletableFuture.completedFuture(failResult);
    }

    /** 查询已注册的智能体列表 */
    public List<String> listAgents() {
        return List.copyOf(agentRegistry.keySet());
    }

    public int agentCount() {
        return agentRegistry.size();
    }

    public List<AgentRunLog> getRunLogs() {
        return List.copyOf(runLogs);
    }

    private void addRunLog(AgentTask task, AgentResult result, int attempt) {
        String input = task.getPayload() != null ? task.getPayload().toString() : "";
        String output = result.getData() != null ? result.getData().toString() : "";
        AgentRunLog logEntry = AgentRunLog.of(
                task.getTaskId(), task.getAgentType(), input,
                output, result.isSuccess(), result.getErrorMessage(),
                result.getDurationMs());
        synchronized (runLogs) {
            runLogs.addFirst(logEntry);
            while (runLogs.size() > MAX_RUN_LOGS) {
                runLogs.removeLast();
            }
        }
    }

    private boolean isNetworkError(Exception e) {
        if (e == null) return false;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        return msg.contains("timeout") || msg.contains("connect") || msg.contains("connection refused")
                || msg.contains("502") || msg.contains("503") || msg.contains("504")
                || e instanceof java.net.SocketException || e instanceof java.net.SocketTimeoutException;
    }
}
