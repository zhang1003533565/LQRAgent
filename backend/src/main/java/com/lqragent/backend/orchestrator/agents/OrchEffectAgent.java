package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Effect 智能体（Orchestrator 版本）
 */
@Component("orchEffectAgent")
public class OrchEffectAgent extends OrchBaseAgent {

    public OrchEffectAgent(RedisStreamsService streams, ApplicationContext applicationContext) {
        super(AgentIds.EFFECT, streams, applicationContext);
    }
    
    @Override
    protected String getAgentBeanName() {
        return "effectAgent";
    }
}
