package com.lqragent.backend.systemconfig;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.lqragent.backend.systemconfig.service.SysConfigService;

import lombok.RequiredArgsConstructor;

/**
 * 运行时配置：数据库 sys_config 优先，否则回退 application.properties。
 */
@Component
@RequiredArgsConstructor
public class AppRuntimeConfig {

    private final SysConfigService sysConfigService;
    private final Environment environment;

    public String get(String key) {
        return sysConfigService.getValue(key)
                .filter(v -> !v.isBlank())
                .orElseGet(() -> environment.getProperty(key, ""));
    }

    public String get(String key, String defaultValue) {
        String value = get(key);
        return value.isBlank() ? defaultValue : value;
    }

    public String getAiServerBaseUrl() {
        return get(ConfigKeys.AI_SERVER_BASE_URL, "http://localhost:8001");
    }

    public String getAiServerWsUrl() {
        String configured = get(ConfigKeys.AI_SERVER_WS_URL, "");
        if (!configured.isBlank()) {
            return configured;
        }
        String baseUrl = getAiServerBaseUrl();
        if (baseUrl.startsWith("https://")) {
            return "wss://" + baseUrl.substring("https://".length()) + "/api/v1/ws";
        }
        if (baseUrl.startsWith("http://")) {
            return "ws://" + baseUrl.substring("http://".length()) + "/api/v1/ws";
        }
        return "ws://localhost:8001/api/v1/ws";
    }

    public boolean isAiServerAutoStart() {
        return Boolean.parseBoolean(get(ConfigKeys.AI_SERVER_AUTO_START, "true"));
    }

    public long getUploadWorkerIntervalMs() {
        return Long.parseLong(get(ConfigKeys.UPLOAD_WORKER_INTERVAL_MS, "5000"));
    }

    public int getUploadMaxConcurrent() {
        return Integer.parseInt(get(ConfigKeys.UPLOAD_MAX_CONCURRENT, "2"));
    }

    /** ai-server WebSocket 连接超时（秒） */
    public int getWsConnectTimeoutSec() {
        return Integer.parseInt(get(ConfigKeys.AI_SERVER_WS_CONNECT_TIMEOUT_SEC, "10"));
    }

    /** ai-server WebSocket 响应等待超时（秒） */
    public int getWsResponseTimeoutSec() {
        return Integer.parseInt(get(ConfigKeys.AI_SERVER_WS_RESPONSE_TIMEOUT_SEC, "120"));
    }

    /** 是否使用 ai-server Agentic Pipeline（false=走旧方案，Java 自己做 AI） */
    public boolean isUseAgenticPipeline() {
        return Boolean.parseBoolean(get(ConfigKeys.AI_SERVER_USE_AGENTIC_PIPELINE, "false"));
    }

    // --- Phase 1：规划 Gate ---

    public boolean isPlanningGateEnabled() {
        return Boolean.parseBoolean(get(ConfigKeys.PLANNING_GATE_ENABLED, "false"));
    }

    public boolean isPlanningGateForceClarify() {
        return Boolean.parseBoolean(get(ConfigKeys.PLANNING_GATE_FORCE_CLARIFY, "false"));
    }

    public boolean isPlanningGatePreferCorePath() {
        return Boolean.parseBoolean(get(ConfigKeys.PLANNING_GATE_PREFER_CORE_PATH, "true"));
    }

    public boolean isPlanningSessionStateEnabled() {
        return Boolean.parseBoolean(get(ConfigKeys.PLANNING_SESSION_STATE_ENABLED, "false"));
    }

    // --- Phase 1：流式输出 ---

    /** 层 1：Pipeline 进度流式（pipeline_start / step running） */
    public boolean isStreamProgressEnabled() {
        return Boolean.parseBoolean(get(ConfigKeys.STREAM_PROGRESS_ENABLED, "false"));
    }

    /** 进度流式开启时，Pipeline 结束不再一次性推送整段 finalContent */
    public boolean isStreamProgressSkipFinalDump() {
        return Boolean.parseBoolean(get(ConfigKeys.STREAM_PROGRESS_SKIP_FINAL_DUMP, "true"));
    }

    public boolean isLlmStreamEnabled() {
        return Boolean.parseBoolean(get(ConfigKeys.LLM_STREAM_ENABLED, "false"));
    }

    public String getLlmStreamScenes() {
        return get(ConfigKeys.LLM_STREAM_SCENES, "qa,learning_path");
    }

    public boolean isLlmStreamQaFastPath() {
        return Boolean.parseBoolean(get(ConfigKeys.LLM_STREAM_QA_FAST_PATH, "false"));
    }

    /** 某场景是否启用 LLM token 流式 */
    public boolean isLlmStreamSceneEnabled(String scene) {
        if (!isLlmStreamEnabled() || scene == null || scene.isBlank()) {
            return false;
        }
        for (String s : getLlmStreamScenes().split(",")) {
            if (s.trim().equalsIgnoreCase(scene.trim())) {
                return true;
            }
        }
        return false;
    }

    public boolean isPathStagedDeliveryEnabled() {
        return Boolean.parseBoolean(get(ConfigKeys.PATH_STAGED_DELIVERY_ENABLED, "true"));
    }

    // --- Phase 2：Agent 协商 ---

    public boolean isConsultationEnabled() {
        return Boolean.parseBoolean(get(ConfigKeys.CONSULTATION_ENABLED, "false"));
    }

    public String getConsultationScenes() {
        return get(ConfigKeys.CONSULTATION_SCENES, "path_generation");
    }

    public int getConsultationMaxRounds() {
        return Integer.parseInt(get(ConfigKeys.CONSULTATION_MAX_ROUNDS, "2"));
    }

    public boolean isConsultationStreamTranscript() {
        return Boolean.parseBoolean(get(ConfigKeys.CONSULTATION_STREAM_TRANSCRIPT, "false"));
    }

    public long getConsultationTimeoutMs() {
        return Long.parseLong(get(ConfigKeys.CONSULTATION_TIMEOUT_MS, "90000"));
    }

    public boolean isConsultationSceneEnabled(String scene) {
        if (!isConsultationEnabled() || scene == null || scene.isBlank()) {
            return false;
        }
        for (String s : getConsultationScenes().split(",")) {
            if (s.trim().equalsIgnoreCase(scene.trim())) {
                return true;
            }
        }
        return false;
    }

    // --- Phase 3：Supervisor ---

    public boolean isSupervisorEnabled() {
        return Boolean.parseBoolean(get(ConfigKeys.SUPERVISOR_ENABLED, "false"));
    }

    public String getSupervisorScenes() {
        return get(ConfigKeys.SUPERVISOR_SCENES, "path_generation,quiz_design");
    }

    public boolean isSupervisorPersistTranscript() {
        return Boolean.parseBoolean(get(ConfigKeys.SUPERVISOR_PERSIST_TRANSCRIPT, "true"));
    }

    public boolean isSupervisorStreamLive() {
        return Boolean.parseBoolean(get(ConfigKeys.SUPERVISOR_STREAM_LIVE, "false"));
    }

    public boolean isSupervisorSceneEnabled(String scene) {
        if (!isSupervisorEnabled() || scene == null || scene.isBlank()) {
            return false;
        }
        for (String s : getSupervisorScenes().split(",")) {
            if (s.trim().equalsIgnoreCase(scene.trim())) {
                return true;
            }
        }
        return false;
    }
}
