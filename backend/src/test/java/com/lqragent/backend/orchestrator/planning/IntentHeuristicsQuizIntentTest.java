package com.lqragent.backend.orchestrator.planning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntentHeuristicsQuizIntentTest {

    @Test
    void detectsQuizGeneration() {
        assertTrue(IntentHeuristics.isQuizGenerationIntent("出 5 道闭包练习题"));
        assertTrue(IntentHeuristics.isQuizGenerationIntent("给我出 5 道 Python 变量练习题"));
        assertTrue(IntentHeuristics.isQuizGenerationIntent("出题：变量与类型"));
    }

    @Test
    void doesNotMatchPureQa() {
        assertFalse(IntentHeuristics.isQuizGenerationIntent("什么是闭包？"));
        assertFalse(IntentHeuristics.isQuizGenerationIntent("什么是 Python 装饰器"));
    }

    @Test
    void doesNotMatchMediaGeneration() {
        assertFalse(IntentHeuristics.isQuizGenerationIntent("画一张装饰器示意图"));
        assertFalse(IntentHeuristics.isQuizGenerationIntent("用视频解释什么是 Agent"));
    }
}
