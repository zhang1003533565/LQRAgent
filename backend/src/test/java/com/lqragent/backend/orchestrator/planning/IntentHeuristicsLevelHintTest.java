package com.lqragent.backend.orchestrator.planning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntentHeuristicsLevelHintTest {

    @Test
    void recognizesYiDianJiChu() {
        assertTrue(IntentHeuristics.hasLevelHint("有一点基础"));
        assertTrue(IntentHeuristics.hasLevelHint("我想学python\n补充信息：有一点基础"));
    }

    @Test
    void sufficientWithPythonAndLevel() {
        assertTrue(IntentHeuristics.hasSufficientLearningDetails("我想学python\n补充信息：有一点基础"));
    }

    @Test
    void vagueOnlyTopicStillInsufficient() {
        assertFalse(IntentHeuristics.hasSufficientLearningDetails("我想学python"));
    }
}
