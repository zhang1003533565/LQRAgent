package com.lqragent.backend.chat.proxy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;

import lombok.extern.slf4j.Slf4j;

/**
 * 将 sys_config 中的模型 API 配置同步到 ai-server 目录下的 .env 文件。
 */
@Slf4j
@Service
public class AiServerEnvSyncService {

    private static final Pattern ENV_LINE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");

    private static final List<String> AI_SERVER_DIR_CANDIDATES = List.of("ai-server", "../ai-server");

    private final AppRuntimeConfig runtimeConfig;
    private com.lqragent.backend.config.AIServerRunner aiServerRunner;

    public AiServerEnvSyncService(AppRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    /** 注入 AIServerRunner（避免循环依赖，用 setter） */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAiServerRunner(com.lqragent.backend.config.AIServerRunner runner) {
        this.aiServerRunner = runner;
    }

    public Optional<Path> resolveEnvFile() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        for (String candidate : AI_SERVER_DIR_CANDIDATES) {
            Path dir = cwd.resolve(candidate).normalize();
            if (Files.isDirectory(dir)) {
                return Optional.of(dir.resolve(".env"));
            }
        }
        return Optional.empty();
    }

    public void syncModelConfigToEnv() throws IOException {
        Path envPath = resolveEnvFile()
                .orElseThrow(() -> new IllegalStateException("未找到 ai-server 目录，无法写入 .env"));

        Map<String, String> updates = buildUpdates();
        mergeEnvFile(envPath, updates);
        log.info("[AiServerEnvSync] 已同步模型配置到 {}", envPath.toAbsolutePath());

        // 自动重启 ai-server 使新配置生效
        if (aiServerRunner != null) {
            log.info("[AiServerEnvSync] 正在重启 ai-server 使配置生效...");
            new Thread(() -> aiServerRunner.restart(), "ai-server-restart").start();
        }
    }

    private Map<String, String> buildUpdates() {
        Map<String, String> updates = new LinkedHashMap<>();
        putIfPresent(updates, "LLM_BINDING", runtimeConfig.get(ConfigKeys.LLM_BINDING));
        putIfPresent(updates, "LLM_MODEL", runtimeConfig.get(ConfigKeys.LLM_MODEL));
        putIfPresent(updates, "LLM_API_KEY", runtimeConfig.get(ConfigKeys.LLM_API_KEY));
        putIfPresent(updates, "LLM_HOST", runtimeConfig.get(ConfigKeys.LLM_HOST));
        putIfPresent(updates, "LLM_API_VERSION", runtimeConfig.get(ConfigKeys.LLM_API_VERSION));
        putIfPresent(updates, "EMBEDDING_BINDING", runtimeConfig.get(ConfigKeys.EMBEDDING_BINDING));
        putIfPresent(updates, "EMBEDDING_MODEL", runtimeConfig.get(ConfigKeys.EMBEDDING_MODEL));
        putIfPresent(updates, "EMBEDDING_API_KEY", runtimeConfig.get(ConfigKeys.EMBEDDING_API_KEY));
        putIfPresent(updates, "EMBEDDING_HOST", runtimeConfig.get(ConfigKeys.EMBEDDING_HOST));
        putIfPresent(updates, "VIDEO_BINDING", runtimeConfig.get(ConfigKeys.VIDEO_BINDING));
        putIfPresent(updates, "VIDEO_MODEL", runtimeConfig.get(ConfigKeys.VIDEO_MODEL));
        putIfPresent(updates, "VIDEO_API_KEY", runtimeConfig.get(ConfigKeys.VIDEO_API_KEY));
        putIfPresent(updates, "VIDEO_HOST", runtimeConfig.get(ConfigKeys.VIDEO_HOST));
        putIfPresent(updates, "IMAGE_BINDING", runtimeConfig.get(ConfigKeys.IMAGE_BINDING));
        putIfPresent(updates, "IMAGE_MODEL", runtimeConfig.get(ConfigKeys.IMAGE_MODEL));
        putIfPresent(updates, "IMAGE_API_KEY", runtimeConfig.get(ConfigKeys.IMAGE_API_KEY));
        putIfPresent(updates, "IMAGE_HOST", runtimeConfig.get(ConfigKeys.IMAGE_HOST));
        putIfPresent(updates, "OCR_BINDING", runtimeConfig.get(ConfigKeys.OCR_BINDING));
        putIfPresent(updates, "OCR_MODEL", runtimeConfig.get(ConfigKeys.OCR_MODEL));
        putIfPresent(updates, "OCR_API_KEY", runtimeConfig.get(ConfigKeys.OCR_API_KEY));
        putIfPresent(updates, "OCR_SECRET_KEY", runtimeConfig.get(ConfigKeys.OCR_SECRET_KEY));
        putIfPresent(updates, "OCR_HOST", runtimeConfig.get(ConfigKeys.OCR_HOST));
        return updates;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private void mergeEnvFile(Path envPath, Map<String, String> updates) throws IOException {
        Files.createDirectories(envPath.getParent());
        List<String> lines = Files.exists(envPath)
                ? new ArrayList<>(Files.readAllLines(envPath, StandardCharsets.UTF_8))
                : new ArrayList<>();

        Set<String> merged = new HashSet<>();
        List<String> out = new ArrayList<>();

        for (String line : lines) {
            Matcher m = ENV_LINE.matcher(line.trim());
            if (m.matches()) {
                String key = m.group(1);
                if (updates.containsKey(key)) {
                    out.add(key + "=" + escapeEnvValue(updates.get(key)));
                    merged.add(key);
                    continue;
                }
            }
            out.add(line);
        }

        for (Map.Entry<String, String> e : updates.entrySet()) {
            if (!merged.contains(e.getKey())) {
                out.add(e.getKey() + "=" + escapeEnvValue(e.getValue()));
            }
        }

        Files.write(envPath, out, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static String escapeEnvValue(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(" ") || value.contains("#")) {
            return "\"" + value.replace("\"", "\\\"") + "\"";
        }
        return value;
    }
}
