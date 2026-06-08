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
 * Effect 智能体（Orchestrator 版本）
 */
@Component("orchEffectAgent")
public class OrchEffectAgent extends OrchBaseAgent {

    private final CapabilityRegistry capabilityRegistry;

    public OrchEffectAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                           CapabilityRegistry capabilityRegistry, AgentMemory agentMemory) {
        super(AgentIds.EFFECT, streams, applicationContext, agentMemory);
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerCapability() {
        setCapabilityRegistry(capabilityRegistry);
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.EFFECT)
                .displayName("学习效果评估Agent")
                .description("学习效果评估、薄弱点分析、学习诊断")
                .actions(List.of("evaluate", "diagnose", "analyze_effect"))
                .tags(Set.of("effect", "evaluate", "diagnose", "weakness"))
                .avgLatencyMs(20000)
                .build());
    }

    @Override
    protected String getAgentBeanName() {
        return "effectAgent";
    }
}
