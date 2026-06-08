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
 * Quality 智能体（Orchestrator 版本）
 */
@Component("orchQualityAgent")
public class OrchQualityAgent extends OrchBaseAgent {

    private final CapabilityRegistry capabilityRegistry;

    public OrchQualityAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                            CapabilityRegistry capabilityRegistry, AgentMemory agentMemory) {
        super(AgentIds.QUALITY, streams, applicationContext, agentMemory);
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerCapability() {
        setCapabilityRegistry(capabilityRegistry);
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.QUALITY)
                .displayName("质量检查Agent")
                .description("质量检查、内容评估、校验资源可用性")
                .actions(List.of("check", "evaluate", "validate"))
                .tags(Set.of("quality", "check", "evaluate", "review"))
                .avgLatencyMs(15000)
                .build());
    }

    @Override
    protected String getAgentBeanName() {
        return "qualityAgent";
    }
}
