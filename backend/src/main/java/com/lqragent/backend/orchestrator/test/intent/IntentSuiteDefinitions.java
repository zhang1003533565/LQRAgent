package com.lqragent.backend.orchestrator.test.intent;

import java.util.List;

/**
 * PlanningAgent 意图回归用例定义（REST / JUnit / CI 共用）
 */
public final class IntentSuiteDefinitions {

    private IntentSuiteDefinitions() {}

    public record IntentCase(
            String input,
            List<String> expectedPlanTypes,
            List<String> expectedAgentIds
    ) {}

    public static List<IntentCase> all() {
        return List.of(
                new IntentCase("你好", List.of("SIMPLE"), List.of()),
                new IntentCase("你能做什么", List.of("SIMPLE"), List.of()),
                new IntentCase("什么是 Python 装饰器", List.of("SIMPLE", "PLAN", "PIPELINE"), List.of("qa_agent")),
                new IntentCase("帮我学 Python", List.of("PLAN", "PIPELINE", "CLARIFY"), List.of("learning_path")),
                new IntentCase("出 5 道闭包练习题", List.of("PLAN", "PIPELINE"), List.of("quiz_agent")),
                new IntentCase("用视频解释什么是 Agent", List.of("PLAN", "PIPELINE"), List.of("media_gen")),
                new IntentCase("画一张装饰器示意图", List.of("PLAN", "PIPELINE"), List.of("media_gen")),
                new IntentCase("生成 Python 学习路线图", List.of("PLAN", "PIPELINE"), List.of("learning_path"))
        );
    }

    /** 不调用 LLM 的快通道用例 */
    public static List<IntentCase> fastPathOnly() {
        return List.of(
                new IntentCase("你好", List.of("SIMPLE"), List.of()),
                new IntentCase("你能做什么", List.of("SIMPLE"), List.of())
        );
    }
}
