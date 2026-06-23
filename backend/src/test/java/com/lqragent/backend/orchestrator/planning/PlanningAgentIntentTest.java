package com.lqragent.backend.orchestrator.planning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lqragent.backend.orchestrator.card.AgentCardRegistry;
import com.lqragent.backend.orchestrator.planning.PlanResult.PlanType;
import com.lqragent.backend.orchestrator.test.intent.IntentSuiteDefinitions;
import com.lqragent.backend.orchestrator.test.intent.IntentSuiteEvaluator;

/**
 * PlanningAgent 意图回归 — 默认 CI 运行（不依赖 LLM / 数据库）
 */
class PlanningAgentIntentTest {

    private PlanningAgent planningAgent;

    @BeforeEach
    void setUp() {
        AgentCardRegistry registry = new AgentCardRegistry();
        PlanPromptProvider promptProvider = new PlanPromptProvider(registry);
        planningAgent = new PlanningAgent(new NoOpLlmClient(), registry, promptProvider);
    }

    @Test
    void intentSuiteDefinitions_coverCoreScenarios() {
        assertEquals(8, IntentSuiteDefinitions.all().size());
        assertEquals(2, IntentSuiteDefinitions.fastPathOnly().size());
    }

    @Test
    void fastPath_greetingDoesNotCallLlm() {
        PlanResult plan = planningAgent.decompose("你好", "1");
        assertEquals(PlanType.SIMPLE, plan.type());
        assertEquals(PlanIntent.GREETING, plan.intent());
    }

    @Test
    void fastPath_helpDoesNotCallLlm() {
        PlanResult plan = planningAgent.decompose("你能做什么", "1");
        assertEquals(PlanType.SIMPLE, plan.type());
        assertEquals(PlanIntent.HELP, plan.intent());
    }

    @Test
    void evaluator_acceptsSimpleGreeting() {
        var definition = IntentSuiteDefinitions.fastPathOnly().get(0);
        var result = IntentSuiteEvaluator.evaluate(definition, PlanResult.simple(PlanIntent.GREETING));
        assertTrue(result.passed());
    }

    @Test
    void evaluator_acceptsPipelineWithExpectedAgent() {
        var definition = IntentSuiteDefinitions.all().stream()
                .filter(c -> c.input().contains("闭包"))
                .findFirst()
                .orElseThrow();
        var plan = PlanResult.pipeline(
                com.lqragent.backend.orchestrator.pipeline.PipelineConfig.builder()
                        .pipelineId("test")
                        .name("test")
                        .steps(java.util.List.of(
                                com.lqragent.backend.orchestrator.pipeline.PipelineStep.builder()
                                        .stepId("s1")
                                        .agentId("quiz_agent")
                                        .action("generate")
                                        .build()))
                        .build(),
                java.util.List.of());
        var result = IntentSuiteEvaluator.evaluate(definition, plan);
        assertTrue(result.passed());
    }
}
