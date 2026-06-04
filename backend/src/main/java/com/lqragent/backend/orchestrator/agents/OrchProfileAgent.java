package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Profile 智能体（Orchestrator 版本）
 */
@Component("orchProfileAgent")
public class OrchProfileAgent extends OrchBaseAgent {

    public OrchProfileAgent(RedisStreamsService streams, ApplicationContext applicationContext) {
        super(AgentIds.PROFILE, streams, applicationContext);
    }
    
    @Override
    protected String getAgentBeanName() {
        return "profileAgent";
    }
}
