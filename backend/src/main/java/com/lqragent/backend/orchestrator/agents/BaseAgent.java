package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;
import com.lqragent.backend.orchestrator.message.Performative;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 智能体基类
 * 所有业务智能体继承此类，实现 process 方法即可
 */
@Slf4j
public abstract class BaseAgent {

    protected final String agentId;
    protected final RedisStreamsService streams;
    private volatile boolean running = false;
    private Thread consumerThread;

    protected BaseAgent(String agentId, RedisStreamsService streams) {
        this.agentId = agentId;
        this.streams = streams;
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
                        if (msg.getPerformative() == Performative.REQUEST) {
                            try {
                                AgentMessage result = process(msg);
                                streams.send("stream:agent:events", result);
                            } catch (Exception e) {
                                log.error("[{}] process failed: {}", agentId, e.getMessage(), e);
                                streams.send("stream:agent:events",
                                        AgentMessage.error(msg.getTaskId(), agentId, e.getMessage()));
                            }
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
