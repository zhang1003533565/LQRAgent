package com.lqragent.backend.orchestrator.test.intent;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentCaseResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentSuiteResult;
import com.lqragent.backend.orchestrator.test.intent.IntentSuiteDefinitions.IntentCase;

/**
 * 批量运行意图回归用例
 */
@Component
public class IntentSuiteRunner {

    @FunctionalInterface
    public interface PlanResolver {
        PlanResult resolve(String userId, String message);
    }

    public IntentSuiteResult run(String userId, PlanResolver resolver) {
        return run(IntentSuiteDefinitions.all(), userId, resolver);
    }

    public IntentSuiteResult run(List<IntentCase> cases, String userId, PlanResolver resolver) {
        long start = System.currentTimeMillis();
        List<IntentCaseResult> results = new ArrayList<>();
        int passed = 0;

        for (IntentCase definition : cases) {
            PlanResult plan = resolver.resolve(userId, definition.input());
            IntentCaseResult result = IntentSuiteEvaluator.evaluate(definition, plan);
            results.add(result);
            if (result.passed()) {
                passed++;
            }
        }

        return new IntentSuiteResult(
                cases.size(),
                passed,
                cases.size() - passed,
                System.currentTimeMillis() - start,
                results
        );
    }
}
