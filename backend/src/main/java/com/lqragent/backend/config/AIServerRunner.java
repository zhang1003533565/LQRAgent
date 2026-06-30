package com.lqragent.backend.config;

import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 按配置决定是否启动 ai-server；日志写入 logs/ai-server.log，避免占用控制台输入。
 */
@Component
@Order(5)
public class AIServerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AIServerRunner.class);

    private static final List<String> AI_SERVER_DIR_CANDIDATES = List.of(
            "ai-server",
            "../ai-server"
    );

    private final AppRuntimeConfig runtimeConfig;

    private Process aiProcess;

    public AIServerRunner(AppRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!runtimeConfig.isAiServerAutoStart()) {
            log.info("[AIServerRunner] ai-server.auto-start=false，跳过自动启动（可在控制台 set ai-server.auto-start true 后重启）");
            return;
        }

        File workDir = resolveAiServerDir()
                .orElseThrow(() -> new IllegalStateException(
                        "Could not find ai-server directory. Check the project layout."
                ));

        if (isAiServerAlreadyUp()) {
            log.info("[AIServerRunner] AI Server 已在 {} 运行，跳过安装与启动", runtimeConfig.getAiServerBaseUrl());
            return;
        }

        try {
            installDependencies(workDir);
        } catch (Exception e) {
            log.warn("[AIServerRunner] AI Server 依赖安装失败（可能被沙箱限制），将继续尝试启动已安装的 AI Server: {}", e.getMessage());
        }
        startAiServer(workDir);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (aiProcess != null && aiProcess.isAlive()) {
                log.info("[AIServerRunner] Stopping AI Server process (PID: {})", aiProcess.pid());
                aiProcess.destroy();
                try {
                    if (!aiProcess.waitFor(10, TimeUnit.SECONDS)) {
                        log.warn("[AIServerRunner] AI Server did not exit in 10 seconds; forcing shutdown");
                        aiProcess.destroyForcibly();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("[AIServerRunner] Interrupted while waiting for AI Server to stop", e);
                }
            }
        }, "ai-server-shutdown-hook"));
    }

    private boolean isAiServerAlreadyUp() {
        try {
            java.net.URL url = new java.net.URL(runtimeConfig.getAiServerBaseUrl() + "/health");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private Optional<File> resolveAiServerDir() throws Exception {
        Path currentDir = Paths.get("").toAbsolutePath().normalize();

        for (String candidate : AI_SERVER_DIR_CANDIDATES) {
            File dir = currentDir.resolve(candidate).toFile().getCanonicalFile();
            if (dir.exists() && dir.isDirectory()) {
                log.info("[AIServerRunner] Using ai-server directory: {}", dir.getAbsolutePath());
                return Optional.of(dir);
            }
        }

        log.error("[AIServerRunner] Could not find ai-server directory from {}", currentDir);
        return Optional.empty();
    }

    private void installDependencies(File workDir) throws Exception {
        List<String> pipCmd = new ArrayList<>(pythonCommand());
        pipCmd.addAll(List.of("-m", "pip", "install", "-q", "-e", ".[server]"));

        log.info("[AIServerRunner] Installing AI Server dependencies: {}", String.join(" ", pipCmd));

        Process pipProcess = new ProcessBuilder(pipCmd)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(aiServerLogFile()))
                .start();

        int exitCode = pipProcess.waitFor();
        if (exitCode != 0) {
            log.error("[AIServerRunner] Dependency install failed with exit code {}, see logs/ai-server.log", exitCode);
            throw new IllegalStateException("AI Server dependency install failed: " + exitCode);
        }

        log.info("[AIServerRunner] AI Server dependencies installed");
    }

    private void startAiServer(File workDir) throws Exception {
        List<String> command = new ArrayList<>(pythonCommand());
        command.addAll(List.of("-m", "deeptutor.api.run_server"));

        log.info("[AIServerRunner] Starting AI Server API from {} -> {}", workDir.getAbsolutePath(),
                runtimeConfig.getAiServerBaseUrl());
        log.info("[AIServerRunner] Command: {}", String.join(" ", command));
        log.info("[AIServerRunner] Process log: {}", aiServerLogFile().getAbsolutePath());

        aiProcess = new ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(aiServerLogFile()))
                .start();

        log.info("[AIServerRunner] AI Server API started with PID {}", aiProcess.pid());
    }

    /**
     * 重启 ai-server（先停后启）。
     */
    public synchronized void restart() {
        log.info("[AIServerRunner] 重启 ai-server...");
        stop();
        try {
            File workDir = resolveAiServerDir().orElse(null);
            if (workDir != null) {
                startAiServer(workDir);
            }
        } catch (Exception e) {
            log.error("[AIServerRunner] 重启失败: {}", e.getMessage());
        }
    }

    private void stop() {
        if (aiProcess != null && aiProcess.isAlive()) {
            log.info("[AIServerRunner] 停止 ai-server (PID: {})", aiProcess.pid());
            aiProcess.destroy();
            try {
                if (!aiProcess.waitFor(10, TimeUnit.SECONDS)) {
                    aiProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            aiProcess = null;
        }
    }

    private static File aiServerLogFile() throws IOException {
        Path logDir = Paths.get("logs");
        Files.createDirectories(logDir);
        return logDir.resolve("ai-server.log").toFile();
    }

    private static List<String> pythonCommand() {
        return isWindows() ? List.of("python") : List.of("python3");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
