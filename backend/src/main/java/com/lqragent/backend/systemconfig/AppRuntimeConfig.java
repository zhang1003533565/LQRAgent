package com.lqragent.backend.systemconfig;

import com.lqragent.backend.systemconfig.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

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
        return get(ConfigKeys.AI_SERVER_WS_URL, "ws://localhost:8001/api/v1/ws");
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
}
