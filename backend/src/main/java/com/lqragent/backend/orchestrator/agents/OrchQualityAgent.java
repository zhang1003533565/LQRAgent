package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Quality 智能体（Orchestrator 版本）
 */
@Component("orchQualityAgent")
public class OrchQualityAgent extends OrchBaseAgent {

    public OrchQualityAgent(RedisStreamsService streams, ApplicationContext applicationContext) {
        super(AgentIds.QUALITY, streams, applicationContext);
    }
    
    @Override
    protected String getAgentBeanName() {
        return "qualityAgent";
    }
}
