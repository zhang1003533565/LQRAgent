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
 * Resource 智能体（Orchestrator 版本）
 */
@Component("orchResourceAgent")
public class OrchResourceAgent extends OrchBaseAgent {

    private final CapabilityRegistry capabilityRegistry;

    public OrchResourceAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                             CapabilityRegistry capabilityRegistry, AgentMemory agentMemory) {
        super(AgentIds.RESOURCE, streams, applicationContext, agentMemory);
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerCapability() {
        setCapabilityRegistry(capabilityRegistry);
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.RESOURCE)
                .displayName("资源生成Agent")
                .description("生成学习资源（讲义、代码示例、Markdown文档、练习题）")
                .actions(List.of("generate_resource", "batch_generate"))
                .tags(Set.of("resource", "material", "content", "generate"))
                .avgLatencyMs(30000)
                .build());
    }

    @Override
    protected String getAgentBeanName() {
        return "resourceAgent";
    }
}
