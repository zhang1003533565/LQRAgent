package com.lqragent.backend.orchestrator.agents;

import com.lqragent.backend.orchestrator.AgentIds;
import com.lqragent.backend.orchestrator.infra.RedisStreamsService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * ContentAnalysis 智能体（Orchestrator 版本）
 */
@Component("orchContentAnalysisAgent")
public class OrchContentAnalysisAgent extends OrchBaseAgent {

    public OrchContentAnalysisAgent(RedisStreamsService streams, ApplicationContext applicationContext) {
        super(AgentIds.CONTENT_ANALYSIS, streams, applicationContext);
    }
    
    @Override
    protected String getAgentBeanName() {
        return "contentAgent";
    }
}
