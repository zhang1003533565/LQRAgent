package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Resource 智能体（Orchestrator 版本）
 */
@Component("orchResourceAgent")
public class OrchResourceAgent extends OrchBaseAgent {

    public OrchResourceAgent(RedisStreamsService streams, ApplicationContext applicationContext) {
        super(AgentIds.RESOURCE, streams, applicationContext);
    }
    
    @Override
    protected String getAgentBeanName() {
        return "resourceAgent";
    }
}
