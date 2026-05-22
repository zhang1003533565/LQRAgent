package com.lqragent.backend.aiserver;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

/**
 * 将 sys_config 中的模型 API 配置同步到 ai-server 目录下的 .env 文件。
 */
@Slf4j
@Service
public class AiServerEnvSyncService {

    private static final Pattern ENV_LINE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)=(.*)$");

    private static final List<String> AI_SERVER_DIR_CANDIDATES = List.of("ai-server", "../ai-server");

    private final AppRuntimeConfig runtimeConfig;

    public AiServerEnvSyncService(AppRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
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
