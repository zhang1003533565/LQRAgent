package com.lqragent.backend.orchestrator.agents;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.agents.learn.path.service.LearningPathService;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.capability.AgentCapability;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import com.lqragent.backend.orchestrator.message.AgentMessage;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

/**
 * 路径规划智能体（Orchestrator 版本）
 * LLM 推理 + 真实 Service 调用
 */
@Slf4j
@Component("orchLearningPathAgent")
public class OrchLearningPathAgent extends OrchBaseAgent {

    private final LearningPathService pathService;
    private final CapabilityRegistry capabilityRegistry;
    private final ObjectMapper mapper = new ObjectMapper();

    public OrchLearningPathAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                                 LearningPathService pathService, CapabilityRegistry capabilityRegistry,
                                 AgentMemory agentMemory) {
        super(AgentIds.LEARNING_PATH, streams, applicationContext, agentMemory);
        this.pathService = pathService;
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerCapability() {
        setCapabilityRegistry(capabilityRegistry);
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.LEARNING_PATH)
                .displayName("学习路径规划Agent")
                .description("规划学习路径、制定学习计划、知识图谱匹配")
                .actions(List.of("generate_path", "plan"))
                .tags(Set.of("learning_path", "path", "plan", "curriculum"))
                .avgLatencyMs(25000)
                .build());
    }
    
    @Override
    protected String getAgentBeanName() {
        return "learningPathAgent";
    }
    
    @Override
    protected Map<String, Object> callService(AgentMessage request, String action) {
        // Orchestrator 发消息时未设置 action，默认 "default"，需兼容处理
        if ("generate_path".equals(action) || "default".equals(action)) {
            try {
                String goal = (String) request.getContent().getOrDefault("goal", "");
                String userIdStr = request.getContent().get("userId") != null
                        ? request.getContent().get("userId").toString() : "2";
                Long userId = Long.parseLong(userIdStr);
                
                log.info("[{}] callService executing with goal='{}', userId={}", agentId, goal, userId);
                var path = pathService.generatePath(userId, goal, null);
                Map<String, Object> result = new java.util.HashMap<>();
                result.put("path", path);
                return result;
            } catch (Exception e) {
                log.error("[{}] callService failed: {}", agentId, e.getMessage(), e);
                return null;
            }
        }
        log.warn("[{}] callService skipped: unknown action '{}'", agentId, action);
        return null;
    }
}
