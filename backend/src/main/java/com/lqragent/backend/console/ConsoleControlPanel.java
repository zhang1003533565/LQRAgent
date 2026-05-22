package com.lqragent.backend.console;

import com.lqragent.backend.aiserver.AiServerClient;
import com.lqragent.backend.config.DataInitializer;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.systemconfig.ConfigKeys;
import com.lqragent.backend.systemconfig.entity.SysConfig;
import com.lqragent.backend.systemconfig.service.SysConfigService;
import com.lqragent.backend.uploadqueue.entity.KbUploadTask;
import com.lqragent.backend.uploadqueue.service.UploadQueueService;
import com.lqragent.backend.user.entity.User;
import com.lqragent.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动完成后在控制台提供交互式管理菜单（不阻塞 Web 服务）。
 */
@Slf4j
@Component
@Order(100)
@RequiredArgsConstructor
public class ConsoleControlPanel implements ApplicationRunner {

    private final SysConfigService sysConfigService;
    private final AppRuntimeConfig runtimeConfig;
    private final UserRepository userRepository;
    private final UploadQueueService uploadQueueService;
    private final AiServerClient aiServerClient;
    private final DataInitializer dataInitializer;

    @Value("${app.console.enabled:true}")
    private boolean consoleEnabled;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public void run(ApplicationArguments args) {
        if (!consoleEnabled) {
            log.info("[Console] 控制台已禁用 (app.console.enabled=false)");
            return;
        }

        Thread consoleThread = new Thread(this::loop, "lqragent-console");
        consoleThread.setDaemon(true);
        consoleThread.start();
    }

    private void loop() {
        running.set(true);
        useUtf8Console();
        printBanner();
        printHelp();

        try (Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8)) {
            while (running.get()) {
                System.out.print("\nLQRAgent> ");
                if (!scanner.hasNextLine()) {
                    break;
                }
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                try {
                    dispatch(line);
                } catch (Exception e) {
                    System.out.println("[错误] " + e.getMessage());
                    log.warn("[Console] command failed: {}", line, e);
                }
            }
        } catch (Exception e) {
            log.debug("[Console] stdin closed: {}", e.getMessage());
        }
    }

    private void dispatch(String line) {
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help", "h", "?" -> printHelp();
            case "config", "c" -> handleConfig(parts);
            case "set" -> handleSet(parts);
            case "del", "delete" -> handleDelete(parts);
            case "users", "u" -> listUsers();
            case "upload", "up" -> handleUpload(parts);
            case "ai" -> handleAi(parts);
            case "api" -> printApiHelp();
            case "sync" -> syncTestUsers();
            case "status", "s" -> printStatus();
            case "quit", "q", "exit" -> {
                running.set(false);
                System.out.println("[Console] 已退出菜单（后端与 AI 服务继续运行）");
            }
            default -> System.out.println("未知命令: " + cmd + "，输入 help 查看帮助");
        }
    }

    private void handleConfig(String[] parts) {
        if (parts.length >= 2) {
            String key = parts[1];
            sysConfigService.findByKey(key).ifPresentOrElse(
                    c -> System.out.printf("  %s = %s%n  备注: %s%n", c.getConfigKey(), c.getConfigValue(),
                            c.getRemark() == null ? "-" : c.getRemark()),
                    () -> System.out.println("  未找到配置项，当前生效(properties): " + runtimeConfig.get(key))
            );
            return;
        }
        System.out.println("--- 运行时配置 (sys_config 优先) ---");
        for (SysConfig c : sysConfigService.listAll()) {
            System.out.printf("  %-32s = %s%n", c.getConfigKey(), truncate(c.getConfigValue(), 60));
        }
        System.out.println("--- 解析后的关键项 ---");
        System.out.println("  server.port              = " + runtimeConfig.get(ConfigKeys.SERVER_PORT, "8080"));
        System.out.println("  ai-server.base-url       = " + runtimeConfig.getAiServerBaseUrl());
        System.out.println("  ai-server.ws-url         = " + runtimeConfig.getAiServerWsUrl());
        System.out.println("  ai-server.auto-start     = " + runtimeConfig.isAiServerAutoStart());
        System.out.println("  upload.worker-interval   = " + runtimeConfig.getUploadWorkerIntervalMs() + " ms");
    }

    private void handleSet(String[] parts) {
        if (parts.length < 3) {
            System.out.println("用法: set <配置键> <配置值> [备注]");
            System.out.println("示例: set ai-server.base-url http://localhost:8001");
            return;
        }
        String key = parts[1];
        String value = parts[2];
        String remark = parts.length > 3 ? parts[3] : null;
        SysConfig saved = sysConfigService.upsert(key, value, remark);
        System.out.println("[OK] 已保存: " + saved.getConfigKey() + " = " + saved.getConfigValue());
        if (ConfigKeys.AI_SERVER_BASE_URL.equals(key)) {
            System.out.println("  提示: AI HTTP 客户端将在下次调用时使用新地址");
        }
        if (ConfigKeys.AI_SERVER_AUTO_START.equals(key)) {
            System.out.println("  提示: 自动启动开关在下次重启后端时生效");
        }
        if (ConfigKeys.UPLOAD_WORKER_INTERVAL_MS.equals(key)) {
            System.out.println("  提示: 上传轮询间隔在下次重启后端时生效");
        }
    }

    private void handleDelete(String[] parts) {
        if (parts.length < 2) {
            System.out.println("用法: del <配置键>");
            return;
        }
        boolean ok = sysConfigService.deleteByKey(parts[1]);
        System.out.println(ok ? "[OK] 已删除，将回退到 application.properties" : "[提示] 配置项不存在");
    }

    private void listUsers() {
        List<User> users = userRepository.findAll();
        if (users.isEmpty()) {
            System.out.println("  (无用户)");
            return;
        }
        System.out.printf("  %-12s %-10s %-16s %s%n", "用户名", "角色", "显示名", "启用");
        for (User u : users) {
            System.out.printf("  %-12s %-10s %-16s %s%n",
                    u.getUsername(), u.getRole(), u.getDisplayName(), u.getEnabled() ? "是" : "否");
        }
        System.out.println("  测试账号密码均为 123456（可用 sync 命令重新同步）");
    }

    private void handleUpload(String[] parts) {
        String sub = parts.length > 1 ? parts[1].toLowerCase() : "list";
        switch (sub) {
            case "list", "ls" -> {
                List<KbUploadTask> tasks = uploadQueueService.listRecent(20);
                if (tasks.isEmpty()) {
                    System.out.println("  (暂无上传任务)");
                    return;
                }
                System.out.printf("  %-6s %-8s %-24s %-10s %s%n", "ID", "状态", "文件名", "范围", "用户ID");
                for (KbUploadTask t : tasks) {
                    System.out.printf("  %-6d %-8s %-24s %-10s %d%n",
                            t.getId(), t.getStatus(), truncate(t.getFileName(), 24), t.getKbScope(), t.getUserId());
                }
            }
            case "stats" -> {
                Map<String, Long> stats = uploadQueueService.countByStatus();
                stats.forEach((k, v) -> System.out.println("  " + k + ": " + v));
            }
            case "run", "process" -> {
                boolean ok = uploadQueueService.processOnePending();
                System.out.println(ok ? "[OK] 已触发处理一条 PENDING 任务" : "[提示] 无待处理任务");
            }
            default -> System.out.println("用法: upload [list|stats|run]");
        }
    }

    private void handleAi(String[] parts) {
        String sub = parts.length > 1 ? parts[1].toLowerCase() : "ping";
        if ("ping".equals(sub) || "test".equals(sub)) {
            boolean ok = aiServerClient.ping();
            System.out.println(ok ? "[OK] AI Server 可达: " + runtimeConfig.getAiServerBaseUrl()
                    : "[失败] 无法连接: " + runtimeConfig.getAiServerBaseUrl());
        } else {
            System.out.println("用法: ai [ping]");
        }
    }

    private void syncTestUsers() {
        dataInitializer.syncDefaultUsersNow();
        System.out.println("[OK] 已同步默认测试账号 admin / student1，密码 123456");
    }

    private void printStatus() {
        System.out.println("--- 服务状态 ---");
        System.out.println("  后端 API     : http://localhost:" + runtimeConfig.get(ConfigKeys.SERVER_PORT, "8080"));
        System.out.println("  前端开发代理 : http://localhost:5173  -> /api /ws -> 8080");
        System.out.println("  AI Server    : " + runtimeConfig.getAiServerBaseUrl());
        System.out.println("  数据库       : " + maskUrl(runtimeConfig.get(ConfigKeys.DATASOURCE_URL)));
        System.out.println("  用户数       : " + userRepository.count());
        System.out.println("  上传任务数   : " + uploadQueueService.totalCount());
    }

    private void printApiHelp() {
        System.out.println("""
                --- 已实现 REST API ---
                  POST /api/auth/login          登录
                  POST /api/auth/logout         登出
                  GET  /api/user/me            当前用户（需 JWT）
                  POST /api/upload              上传文件入队（需 JWT）
                  GET  /api/upload/tasks        上传任务列表（需 JWT）
                  GET  /api/learning-path?goal= 学习路径（需 JWT，占位数据）
                --- 前端 WebSocket（待后端实现代理）---
                  WS   /ws/chat?token=          聊天流式
                --- 常用配置键 ---
                  ai-server.base-url
                  ai-server.ws-url
                  ai-server.auto-start
                  upload.queue.worker-interval-ms
                """);
    }

    private void printBanner() {
        System.out.println("""
                
                ========================================
                  LQRAgent 控制台（启动后配置与管理）
                  输入 help 查看命令
                ========================================
                """);
    }

    private void printHelp() {
        System.out.println("""
                命令列表:
                  help / h          显示帮助
                  status / s        服务与连接概览
                  config / c        列出全部配置；config <键> 查看单项
                  set <键> <值>     保存配置到 sys_config（立即生效或重启生效见提示）
                  del <键>          删除配置项
                  users / u         查看系统用户
                  upload list       最近上传任务
                  upload stats      按状态统计
                  upload run        手动处理一条待处理任务
                  ai ping           测试 AI Server HTTP 连接
                  api               查看 API 列表
                  sync              同步测试账号密码为 123456
                  quit / q          退出控制台菜单
                """);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String maskUrl(String url) {
        if (url == null || url.isBlank()) return "-";
        return url.replaceAll("password=[^&]*", "password=****");
    }

    /** Windows 下 System.out 默认常随 GBK 控制台；统一为 UTF-8 避免中文菜单乱码。 */
    private static void useUtf8Console() {
        try {
            System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 保持默认流
        }
    }
}
