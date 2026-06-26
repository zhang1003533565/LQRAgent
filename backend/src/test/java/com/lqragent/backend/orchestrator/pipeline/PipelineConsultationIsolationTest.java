package com.lqragent.backend.orchestrator.pipeline;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PipelineConsultationIsolationTest {

    @Test
    void learningPathCore_usesPathConsult() {
        assertTrue(PipelineTemplates.learningPathCore().getSteps().stream()
                .anyMatch(s -> "path_consult".equals(s.getStepId())));
    }

    @Test
    void learningPathFull_usesPathConsultNotBarePathGen() {
        var steps = PipelineTemplates.learningPath().getSteps();
        assertTrue(steps.stream().anyMatch(s -> "path_consult".equals(s.getStepId())));
        assertFalse(steps.stream().anyMatch(s -> "path_gen".equals(s.getStepId())));
    }

    @Test
    void qaPipeline_hasNoConsultationStep() {
        assertFalse(PipelineTemplates.questionAnswer().getSteps().stream()
                .anyMatch(s -> "path_consult".equals(s.getStepId()) || "consult_path".equals(s.getAction())));
    }

    @Test
    void mediaPipeline_hasNoConsultationStep() {
        assertFalse(PipelineTemplates.mediaGeneration().getSteps().stream()
                .anyMatch(s -> "path_consult".equals(s.getStepId()) || "consult_path".equals(s.getAction())));
    }
}
