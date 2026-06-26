package com.lqragent.backend.orchestrator.supervisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import com.lqragent.backend.orchestrator.consultation.ConsultationScene;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.service.SysConfigService;

class SupervisorServiceTest {

    @Test
    void disabledByDefault() {
        SupervisorService supervisor = new SupervisorService(defaultConfig());
        PlanResult plan = PlanResult.pipeline(PipelineTemplates.learningPathCore(), null);
        assertFalse(supervisor.needsConsultation(plan));
    }

    @Test
    void resolvesPathSceneWhenEnabled() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("supervisor.enabled", "true");
        env.setProperty("supervisor.scenes", "path_generation");
        SupervisorService supervisor = new SupervisorService(new AppRuntimeConfig(emptySysConfig(), env));

        PipelineConfig config = PipelineTemplates.learningPathCore();
        PlanResult plan = PlanResult.pipeline(config, config.getSteps());
        assertTrue(supervisor.needsConsultation(plan));
        assertTrue(supervisor.resolveScene(plan).filter(s -> s == ConsultationScene.PATH_GENERATION).isPresent());
    }

    @Test
    void run_marksConsultationPendingWhenEnabled() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("supervisor.enabled", "true");
        env.setProperty("supervisor.scenes", "quiz_design");
        SupervisorService supervisor = new SupervisorService(new AppRuntimeConfig(emptySysConfig(), env));

        PipelineConfig config = PipelineTemplates.quiz();
        PlanResult plan = PlanResult.pipeline(config, config.getSteps());
        TaskContext ctx = new TaskContext("t1", "1", null, "出题");

        supervisor.run(plan, ctx);

        assertTrue(Boolean.TRUE.equals(ctx.get("supervisor.consultationPending")));
        assertEquals("QUIZ_DESIGN", ctx.get(SupervisorService.CONTEXT_KEY));
    }

    private static AppRuntimeConfig defaultConfig() {
        return new AppRuntimeConfig(emptySysConfig(), new MockEnvironment());
    }

    private static SysConfigService emptySysConfig() {
        return new SysConfigService(null) {
            @Override
            public java.util.Optional<String> getValue(String key) {
                return java.util.Optional.empty();
            }
        };
    }
}
