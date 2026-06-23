package com.lqragent.backend.orchestrator.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.lqragent.backend.orchestrator.test.OrchestratorTestService;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentSuiteResult;

/**
 * 完整意图回归（需 LLM + MySQL）。
 * 本地/CI 运行：设置环境变量 LQR_INTENT_SUITE=true
 */
@EnabledIfEnvironmentVariable(named = "LQR_INTENT_SUITE", matches = "true")
@SpringBootTest
class PlanningAgentIntentIntegrationTest {

    @Autowired
    private OrchestratorTestService orchestratorTestService;

    @Test
    void fullIntentSuite_shouldMeetMinimumPassRate() {
        IntentSuiteResult result = orchestratorTestService.runIntentSuite("1");
        assertEquals(8, result.total());
        assertTrue(result.passed() >= 6,
                () -> "通过率过低: " + result.passed() + "/" + result.total()
                        + "，失败用例请查看 /api/test/intent-suite");
    }
}
