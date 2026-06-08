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
 * Qa 智能体（Orchestrator 版本）
 */
@Component("orchQaAgent")
public class OrchQaAgent extends OrchBaseAgent {

    private final CapabilityRegistry capabilityRegistry;

    public OrchQaAgent(RedisStreamsService streams, ApplicationContext applicationContext,
                       CapabilityRegistry capabilityRegistry, AgentMemory agentMemory) {
        super(AgentIds.QA, streams, applicationContext, agentMemory);
        this.capabilityRegistry = capabilityRegistry;
    }

    @PostConstruct
    public void registerCapability() {
        setCapabilityRegistry(capabilityRegistry);
        capabilityRegistry.register(AgentCapability.builder()
                .agentId(AgentIds.QA)
                .displayName("智能问答Agent")
                .description("答疑、RAG知识库检索、流式对话")
                .actions(List.of("answer_question", "search_knowledge", "generate_answer"))
                .tags(Set.of("qa", "question", "answer", "knowledge", "chat"))
                .avgLatencyMs(15000)
                .build());
    }

    @Override
    protected String getAgentBeanName() {
        return "qaAgent";
    }
}
