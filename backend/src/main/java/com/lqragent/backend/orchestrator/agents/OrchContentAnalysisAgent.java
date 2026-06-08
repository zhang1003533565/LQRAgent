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
 * ContentAnalysis 智能体（Orchestrator 版本）
 */
@Component("orchContentAnalysisAgent")
public class OrchContentAnalysisAgent extends OrchBaseAgent {

    private final CapabilityRegistry capabilityRegistry;

    public OrchContentAnalysisAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                                    CapabilityRegistry capabilityRegistry, AgentMemory agentMemory) {
        super(AgentIds.CONTENT_ANALYSIS, streams, applicationContext, agentMemory);
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerCapability() {
        setCapabilityRegistry(capabilityRegistry);
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.CONTENT_ANALYSIS)
                .displayName("内容分析Agent")
                .description("分析学习内容、提取知识点、内容结构化")
                .actions(List.of("analyze", "analyze_content", "extract_knowledge"))
                .tags(Set.of("analysis", "content", "knowledge"))
                .avgLatencyMs(20000)
                .build());
    }

    @Override
    protected String getAgentBeanName() {
        return "contentAgent";
    }
}
