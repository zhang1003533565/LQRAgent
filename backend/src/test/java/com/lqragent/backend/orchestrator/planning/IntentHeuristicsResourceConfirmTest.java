package com.lqragent.backend.orchestrator.planning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IntentHeuristicsResourceConfirmTest {

    @Test
    void confirmsShortAffirmatives() {
        assertTrue(IntentHeuristics.isResourceGenerationConfirm("是的"));
        assertTrue(IntentHeuristics.isResourceGenerationConfirm("生成"));
        assertTrue(IntentHeuristics.isResourceGenerationConfirm("好的"));
    }

    @Test
    void confirmsExplicitResourceRequest() {
        assertTrue(IntentHeuristics.isResourceGenerationConfirm("帮我生成讲义"));
        assertTrue(IntentHeuristics.isResourceGenerationConfirm("要生成资源"));
    }

    @Test
    void declinesSingleLetterNoOnly() {
        assertTrue(IntentHeuristics.isResourceGenerationDecline("n"));
        assertTrue(IntentHeuristics.isResourceGenerationDecline("no"));
        assertTrue(IntentHeuristics.isResourceGenerationDecline("否"));
    }

    @Test
    void englishWordsWithLetterN_notDecline() {
        assertFalse(IntentHeuristics.isResourceGenerationDecline("confirm"));
        assertFalse(IntentHeuristics.isResourceGenerationDecline("begin"));
        assertFalse(IntentHeuristics.isResourceGenerationDecline("want"));
        assertFalse(IntentHeuristics.isResourceGenerationDecline("yes"));
    }

    @Test
    void declinesSkipPhrases() {
        assertTrue(IntentHeuristics.isResourceGenerationDecline("不用"));
        assertTrue(IntentHeuristics.isResourceGenerationDecline("跳过"));
        assertTrue(IntentHeuristics.isResourceGenerationDecline("不要了"));
    }

    @Test
    void declineOverridesConfirmKeywords() {
        assertFalse(IntentHeuristics.isResourceGenerationConfirm("不用生成"));
    }

    @Test
    void unrelatedLearningMessageNotConfirm() {
        assertFalse(IntentHeuristics.isResourceGenerationConfirm("我想继续学 Python 数据分析"));
    }
}
