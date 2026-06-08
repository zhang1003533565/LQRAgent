package com.lqragent.backend.orchestrator.agents;

import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.lqragent.backend.agents.base.AgentMemory;
import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.capability.AgentCapability;
import com.lqragent.backend.orchestrator.capability.CapabilityRegistry;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;

import jakarta.annotation.PostConstruct;

/**
 * Profile 智能体（Orchestrator 版本）
 */
@Component("orchProfileAgent")
public class OrchProfileAgent extends OrchBaseAgent {

    private final CapabilityRegistry capabilityRegistry;

    public OrchProfileAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                            CapabilityRegistry capabilityRegistry, AgentMemory agentMemory) {
        super(AgentIds.PROFILE, streams, applicationContext, agentMemory);
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerCapability() {
        setCapabilityRegistry(capabilityRegistry);
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.PROFILE)
                .displayName("用户画像Agent")
                .description("获取学习画像、知识掌握度、学习偏好")
                .actions(List.of("get_profile", "update_profile"))
                .tags(Set.of("profile", "learner", "user"))
                .avgLatencyMs(10000)
                .build());
    }

    @Override
    protected String getAgentBeanName() {
        return "profileAgent";
    }
}
