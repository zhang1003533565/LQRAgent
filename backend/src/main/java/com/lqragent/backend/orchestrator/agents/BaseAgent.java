package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.capability.AgentCapability;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.TimeoutException;

/**
 * 智能体基类
 * <p>
 * 所有业务智能体继承此类，实现 process 方法即可。
 * v2 新增：Agent 对等通信能力（requestPeer / negotiate / cfp）
 */
@Slf4j
public abstract class BaseAgent {

    protected final String agentId;
    protected final RedisStreamsService streams;
    private volatile boolean running = false;
    private Thread consumerThread;

    /** 能力注册中心（可选，由子类注入） */
    protected CapabilityRegistry capabilityRegistry;

    protected BaseAgent(String agentId, RedisStreamsService streams) {
        this.agentId = agentId;
        this.streams = streams;
    }

    /**
     * 设置能力注册中心（子类在构造后调用）
     */
    public void setCapabilityRegistry(CapabilityRegistry registry) {
        this.capabilityRegistry = registry;
    }

    @PostConstruct
    public void start() {
        String stream = "stream:agent:" + agentId;
        String group = "group:" + agentId;
        streams.createGroup(stream, group);

        running = true;
        consumerThread = new Thread(() -> {
            log.info("[{}] started, consuming from {}", agentId, stream);
            while (running) {
                try {
                    var messages = streams.consume(stream, group, agentId + ":worker-1", 1);
                    for (AgentMessage msg : messages) {
                        Performative perf = msg.getPerformative();
                        if (perf == Performative.REQUEST || perf == Performative.REQUEST_PEER) {
                            try {
                                AgentMessage result = process(msg);
                                // REQUEST_PEER → 回复 INFORM_PEER；REQUEST → 回复 INFORM
                                if (perf == Performative.REQUEST_PEER && result.getPerformative() == Performative.INFORM) {
                                    result = AgentMessage.informPeer(
                                            result.getTaskId(), result.getSender(), result.getReceiver(),
                                            result.getContent());
                                }
                                streams.send("stream:agent:events", result);
                            } catch (Exception e) {
                                log.error("[{}] process failed: {}", agentId, e.getMessage(), e);
                                streams.send("stream:agent:events",
                                        AgentMessage.error(msg.getTaskId(), agentId, e.getMessage()));
                            }
                        } else if (perf == Performative.CFP) {
                            // 处理招标请求：评估自身能力，提交提案
                            handleCfp(msg);
                        }
                    }
                } catch (Exception e) {
                    if (running) {
                        log.error("[{}] consume error: {}", agentId, e.getMessage());
                    }
                }
            }
            log.info("[{}] stopped", agentId);
        }, "agent-" + agentId);
        consumerThread.setDaemon(true);
        consumerThread.start();
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (consumerThread != null) {
            consumerThread.interrupt();
        }
    }

    // ==================== 对等通信 API ====================

    /**
     * 向同级 Agent 发送协作请求
     *
     * @param peerAgentId 目标 Agent ID
     * @param taskId      任务ID
     * @param content     请求内容
     */
    protected void requestPeer(String peerAgentId, String taskId, Map<String, Object> content) {
        AgentMessage msg = AgentMessage.requestPeer(taskId, agentId, peerAgentId, content);
        streams.send("stream:agent:" + peerAgentId, msg);
        log.info("[{}] requested peer {}: taskId={}", agentId, peerAgentId, taskId);
    }

    /**
     * 请求同级 Agent 协助并等待结果
     *
     * @param peerAgentId 目标 Agent ID
     * @param taskId      任务ID
     * @param content     请求内容
     * @param timeoutMs   超时（毫秒）
     * @return Agent 返回的结果
     */
    protected Map<String, Object> requestPeerAndWait(String peerAgentId, String taskId,
                                                      Map<String, Object> content, long timeoutMs)
            throws TimeoutException {
        requestPeer(peerAgentId, taskId, content);
        return waitForPeerResult(taskId, peerAgentId, timeoutMs);
    }

    /**
     * 等待同级 Agent 的响应
     */
    protected Map<String, Object> waitForPeerResult(String taskId, String peerAgentId, long timeoutMs)
            throws TimeoutException {
        long startTime = System.currentTimeMillis();
        String stream = "stream:agent:events";
        String group = "group:" + agentId + ":peer";
        String consumer = agentId + ":peer-worker-" + Thread.currentThread().getId();
        streams.createGroup(stream, group);

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            try {
                var messages = streams.consumePending(stream, group, consumer, 10);
                for (AgentMessage msg : messages) {
                    if (!taskId.equals(msg.getTaskId())) continue;
                    if (msg.getPerformative() == Performative.INFORM_PEER
                            && peerAgentId.equals(msg.getSender())) {
                        return msg.getContent();
                    }
                    if (msg.getPerformative() == Performative.ERROR
                            && peerAgentId.equals(msg.getSender())) {
                        throw new RuntimeException(
                                (String) msg.getContent().getOrDefault("error", "Peer error"));
                    }
                    if (msg.getPerformative() == Performative.REFUSE
                            && peerAgentId.equals(msg.getSender())) {
                        throw new RuntimeException("Peer " + peerAgentId + " refused the request");
                    }
                }
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for peer", e);
            }
        }
        throw new TimeoutException("Timeout waiting for peer agent: " + peerAgentId);
    }

    // ==================== 协商协议（CFP） ====================

    /**
     * 发起招标（Call for Proposal）：向多个 Agent 征求方案
     *
     * @param taskId      任务ID
     * @param description 任务描述
     * @param candidates  候选 Agent ID 列表
     * @param timeoutMs   等待提案超时
     * @return 收到的提案列表（agentId → 提案内容）
     */
    protected Map<String, Map<String, Object>> callForProposals(String taskId, String description,
                                                                  List<String> candidates, long timeoutMs) {
        Map<String, Map<String, Object>> proposals = new LinkedHashMap<>();

        // 向所有候选 Agent 发送 CFP
        for (String candidate : candidates) {
            AgentMessage cfp = AgentMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .taskId(taskId)
                    .performative(Performative.CFP)
                    .sender(agentId)
                    .receiver(candidate)
                    .content(Map.of("description", description, "deadline", System.currentTimeMillis() + timeoutMs))
                    .timestamp(System.currentTimeMillis())
                    .build();
            streams.send("stream:agent:" + candidate, cfp);
        }

        // 收集提案
        long deadline = System.currentTimeMillis() + timeoutMs;
        String stream = "stream:agent:events";
        String group = "group:" + agentId + ":cfp";
        String consumer = agentId + ":cfp-worker-" + Thread.currentThread().getId();
        streams.createGroup(stream, group);

        while (System.currentTimeMillis() < deadline && proposals.size() < candidates.size()) {
            try {
                var messages = streams.consumePending(stream, group, consumer, candidates.size());
                for (AgentMessage msg : messages) {
                    if (!taskId.equals(msg.getTaskId())) continue;
                    if (msg.getPerformative() == Performative.PROPOSE) {
                        proposals.put(msg.getSender(), msg.getContent());
                        log.info("[{}] received proposal from {}", agentId, msg.getSender());
                    }
                    if (msg.getPerformative() == Performative.REFUSE) {
                        log.info("[{}] {} refused CFP", agentId, msg.getSender());
                    }
                }
                if (proposals.size() >= candidates.size()) break;
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return proposals;
    }

    /**
     * 接受提案并委托任务
     */
    protected void acceptProposal(String taskId, String winnerAgentId, Map<String, Object> task) {
        AgentMessage confirm = AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .performative(Performative.CONFIRM)
                .sender(agentId)
                .receiver(winnerAgentId)
                .content(task)
                .timestamp(System.currentTimeMillis())
                .build();
        streams.send("stream:agent:" + winnerAgentId, confirm);
        log.info("[{}] confirmed proposal from {}", agentId, winnerAgentId);
    }

    /**
     * 处理收到的 CFP（子类可覆盖以自定义提案逻辑）
     */
    protected void handleCfp(AgentMessage cfp) {
        try {
            // 默认：评估自身是否能处理此任务
            String description = (String) cfp.getContent().getOrDefault("description", "");
            boolean canHandle = evaluateCapability(description);

            if (canHandle) {
                Map<String, Object> proposal = Map.of(
                        "agentId", agentId,
                        "confidence", 0.8,
                        "estimatedDuration", estimateDuration(description),
                        "description", "I can handle this task"
                );
                AgentMessage propose = AgentMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .taskId(cfp.getTaskId())
                        .performative(Performative.PROPOSE)
                        .sender(agentId)
                        .receiver(cfp.getSender())
                        .content(proposal)
                        .timestamp(System.currentTimeMillis())
                        .build();
                streams.send("stream:agent:events", propose);
            } else {
                AgentMessage refuse = AgentMessage.builder()
                        .id(UUID.randomUUID().toString())
                        .taskId(cfp.getTaskId())
                        .performative(Performative.REFUSE)
                        .sender(agentId)
                        .receiver(cfp.getSender())
                        .content(Map.of("reason", "Cannot handle this task"))
                        .timestamp(System.currentTimeMillis())
                        .build();
                streams.send("stream:agent:events", refuse);
            }
        } catch (Exception e) {
            log.error("[{}] handleCfp failed: {}", agentId, e.getMessage());
        }
    }

    /**
     * 评估自身是否能处理某任务（子类覆盖）
     */
    protected boolean evaluateCapability(String taskDescription) {
        if (capabilityRegistry == null) return true;
        return capabilityRegistry.findById(agentId)
                .map(cap -> cap.tags().stream().anyMatch(taskDescription.toLowerCase()::contains)
                        || cap.description().toLowerCase().contains(taskDescription.toLowerCase()))
                .orElse(false);
    }

    /**
     * 估算任务耗时（子类覆盖）
     */
    protected long estimateDuration(String taskDescription) {
        if (capabilityRegistry == null) return 30000;
        return capabilityRegistry.findById(agentId)
                .map(AgentCapability::avgLatencyMs)
                .orElse(30000L);
    }

    // ==================== 能力查询 ====================

    /**
     * 查询可用的同级 Agent
     */
    protected List<AgentCapability> findCapablePeers(String keyword) {
        if (capabilityRegistry == null) return List.of();
        return capabilityRegistry.findByKeyword(keyword);
    }

    /**
     * 查询指定 Agent 的能力
     */
    protected Optional<AgentCapability> getPeerCapability(String peerAgentId) {
        if (capabilityRegistry == null) return Optional.empty();
        return capabilityRegistry.findById(peerAgentId);
    }

    // ==================== 基础方法 ====================

    /**
     * 处理消息（子类实现）
     */
    protected abstract AgentMessage process(AgentMessage request);

    /**
     * 发送进度通知
     */
    protected void sendProgress(String taskId, String message) {
        streams.send("stream:agent:events", AgentMessage.progress(taskId, agentId, message));
    }
}
