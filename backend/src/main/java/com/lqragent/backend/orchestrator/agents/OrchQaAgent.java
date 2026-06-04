package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Qa 智能体（Orchestrator 版本）
 */
@Component("orchQaAgent")
public class OrchQaAgent extends OrchBaseAgent {

    public OrchQaAgent(RedisStreamsService streams, ApplicationContext applicationContext) {
        super(AgentIds.QA, streams, applicationContext);
    }
    
    @Override
    protected String getAgentBeanName() {
        return "qaAgent";
    }
}
