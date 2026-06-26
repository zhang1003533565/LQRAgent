package com.lqragent.backend.orchestrator.supervisor;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.lqragent.backend.orchestrator.consultation.ConsultationScene;
import com.lqragent.backend.orchestrator.context.TaskContext;
import com.lqragent.backend.orchestrator.planning.PlanResult;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Phase 3：Leader 层 Supervisor 骨架。
 * supervisor.enabled=false 时不改变 Phase 2 行为；开启后按场景注册协商意图并写入 TaskContext。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupervisorService {

    public static final String CONTEXT_KEY = "supervisor.scenes";

    private final AppRuntimeConfig runtimeConfig;

    public boolean isEnabled() {
        return runtimeConfig.isSupervisorEnabled();
    }

    /** 当前计划是否需要 Pre-Pipeline 协商（仅 flag 开启且命中场景时） */
    public boolean needsConsultation(PlanResult plan) {
        if (!isEnabled() || plan == null) {
            return false;
        }
        return resolveScene(plan).isPresent();
    }

    /** Pipeline 启动前：解析场景并写入 TaskContext（实际协商在 path_consult / quiz_consult 步骤内） */
    public void run(PlanResult plan, TaskContext context) {
        enrichPipelineContext(plan, context);
        if (context != null && needsConsultation(plan)) {
            context.put("supervisor.consultationPending", true);
        }
    }

    /** Pipeline 启动前写入 Supervisor 元数据，供后续步骤 / 落库使用 */
    public void enrichPipelineContext(PlanResult plan, TaskContext context) {
        if (context == null || !isEnabled()) {
            return;
        }
        resolveScene(plan).ifPresent(scene -> {
            context.put(CONTEXT_KEY, scene.name());
            context.put("supervisor.enabled", true);
            log.info("[Supervisor] scene={} taskId={}", scene, context.getTaskId());
        });
    }

    public Optional<ConsultationScene> resolveScene(PlanResult plan) {
        if (plan == null || plan.pipelineConfig() == null) {
            return Optional.empty();
        }
        String pipelineId = plan.pipelineConfig().getPipelineId();
        if (pipelineId == null) {
            return Optional.empty();
        }
        String normalized = pipelineId.toLowerCase(Locale.ROOT);
        if (matchesPathPipeline(normalized)
                && runtimeConfig.isSupervisorSceneEnabled("path_generation")) {
            return Optional.of(ConsultationScene.PATH_GENERATION);
        }
        if (matchesQuizPipeline(normalized)
                && runtimeConfig.isSupervisorSceneEnabled("quiz_design")) {
            return Optional.of(ConsultationScene.QUIZ_DESIGN);
        }
        return Optional.empty();
    }

    private boolean matchesPathPipeline(String pipelineId) {
        return pipelineId.contains("learning_path");
    }

    private boolean matchesQuizPipeline(String pipelineId) {
        return pipelineId.contains("quiz") || pipelineId.contains("learning_loop");
    }

    public Map<String, Object> statusSnapshot() {
        return Map.of(
                "enabled", isEnabled(),
                "scenes", runtimeConfig.getSupervisorScenes(),
                "persistTranscript", runtimeConfig.isSupervisorPersistTranscript(),
                "streamLive", runtimeConfig.isSupervisorStreamLive());
    }
}
