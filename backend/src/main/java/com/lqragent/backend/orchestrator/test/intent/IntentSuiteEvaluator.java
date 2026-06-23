package com.lqragent.backend.orchestrator.test.intent;

import java.util.List;

import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.test.OrchestratorTestSupport;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentCaseResult;
import com.lqragent.backend.orchestrator.test.intent.IntentSuiteDefinitions.IntentCase;

/**
 * 单条意图用例判定逻辑
 */
public final class IntentSuiteEvaluator {

    private IntentSuiteEvaluator() {}

    public static IntentCaseResult evaluate(IntentCase definition, PlanResult plan) {
        String actualType = plan.type().name();
        List<String> actualAgents = OrchestratorTestSupport.agentIdsFromPlan(plan);

        boolean typeOk = definition.expectedPlanTypes().contains(actualType)
                || (definition.expectedPlanTypes().contains("PIPELINE")
                && ("PLAN".equals(actualType) || "PIPELINE".equals(actualType)));

        boolean agentOk = true;
        if (definition.expectedAgentIds() != null && !definition.expectedAgentIds().isEmpty()) {
            agentOk = definition.expectedAgentIds().stream().anyMatch(actualAgents::contains);
        }

        boolean passed = typeOk && agentOk;
        return new IntentCaseResult(
                definition.input(),
                passed,
                String.join("|", definition.expectedPlanTypes()),
                actualType,
                definition.expectedAgentIds(),
                actualAgents,
                passed ? "OK" : describeFailure(typeOk, agentOk)
        );
    }

    private static String describeFailure(boolean typeOk, boolean agentOk) {
        if (!typeOk && !agentOk) return "planType 与 agentId 均不符合";
        if (!typeOk) return "planType 不符合";
        return "缺少期望 agentId";
    }
}
