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
}
