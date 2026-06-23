package com.lqragent.backend.admin.controller;

import com.lqragent.backend.chat.proxy.AiServerClient;
import com.lqragent.backend.config.DataInitializer;
import com.lqragent.backend.orchestrator.card.AgentCardRegistry;
import com.lqragent.backend.orchestrator.test.OrchestratorTestService;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentCaseResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.IntentSuiteResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PipelineTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PlanStepDto;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PlanTestResult;
import com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.StepResultDto;
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
    private final OrchestratorTestService orchestratorTestService;
    private final AgentCardRegistry agentCardRegistry;

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
        int space = line.indexOf(' ');
        String cmd = (space > 0 ? line.substring(0, space) : line).toLowerCase();
        String args = space > 0 ? line.substring(space + 1).trim() : "";
        String[] parts = line.split("\\s+", 3);

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
            case "plan" -> handlePlan(args);
            case "pipeline", "pipe" -> handlePipeline(args);
            case "agents" -> listAgentCards();
            case "tools" -> listAiServerTools();
            case "capability", "cap" -> handleCapability(args);
            case "loop" -> handleLearningLoop(args);
            case "intent" -> handleIntentSuite();
            case "pipeline-status", "ptask" -> handlePipelineTaskStatus(args);
            case "retry" -> handleRetry(args);
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
        System.out.println("  AI Server    : " + runtimeConfig.getAiServerBaseUrl()
                + (aiServerClient.ping() ? " [可达]" : " [不可达]"));
        System.out.println("  数据库       : " + maskUrl(runtimeConfig.get(ConfigKeys.DATASOURCE_URL)));
        System.out.println("  用户数       : " + userRepository.count());
        System.out.println("  上传任务数   : " + uploadQueueService.totalCount());
        System.out.println("  AgentCard 数 : " + agentCardRegistry.size());
        System.out.println("  Orchestrator : Java 主路径 (use-agentic-pipeline="
                + runtimeConfig.isUseAgenticPipeline() + ")");
        orchestratorTestService.getLatestPipelineTask(1L).ifPresentOrElse(
                t -> System.out.println("  最近 Pipeline: " + t.taskId() + " " + t.status()
                        + " " + t.pipelineName()),
                () -> System.out.println("  最近 Pipeline: (无记录)")
        );
    }

    private void handlePlan(String message) {
        if (message.isBlank()) {
            System.out.println("用法: plan <用户消息>");
            System.out.println("示例: plan 帮我学 Python 装饰器");
            return;
        }
        System.out.println("[plan] 规划中: " + message);
        PlanTestResult r = orchestratorTestService.planOnly("1", message, null);
        printPlanResult(r);
    }

    private void handlePipeline(String message) {
        if (message.isBlank()) {
            System.out.println("用法: pipeline <用户消息>");
            System.out.println("示例: pipeline 出 5 道闭包练习题");
            return;
        }
        System.out.println("[pipeline] 执行中（同步，可能较慢）: " + message);
        long start = System.currentTimeMillis();
        PipelineTestResult r = orchestratorTestService.runPipelineSync("1", message);
        System.out.println("--- 规划 ---");
        printPlanResult(r.plan());
        System.out.println("--- 执行 (" + (System.currentTimeMillis() - start) + "ms) ---");
        if (r.stepResults() == null || r.stepResults().isEmpty()) {
            System.out.println("  (无 Pipeline 步骤，可能是 SIMPLE/CLARIFY)");
        } else {
            int idx = 0;
            for (StepResultDto s : r.stepResults()) {
                idx++;
                String mark = s.success() ? "OK" : "FAIL";
                System.out.printf("  %d. [%s] %s -> %s (%dms)%n",
                        idx, mark, s.stepId(), s.agentId(), s.durationMs());
                if (s.summary() != null && !s.summary().isBlank()) {
                    System.out.println("     " + truncate(s.summary(), 120));
                }
                if (s.error() != null && !s.error().isBlank()) {
                    System.out.println("     错误: " + s.error());
                }
                if (s.artifacts() != null && !s.artifacts().isEmpty()) {
                    System.out.println("     artifacts: " + s.artifacts().size() + " 个");
                }
            }
        }
        if (r.error() != null && !r.error().isBlank()) {
            System.out.println("[失败] " + r.error());
        } else if (r.success()) {
            System.out.println("[完成] 总耗时 " + r.durationMs() + "ms");
        }
    }

    private void printPlanResult(PlanTestResult r) {
        if (r == null) {
            System.out.println("  (无规划结果)");
            return;
        }
        System.out.println("  planType   : " + r.planType());
        if (r.intent() != null) System.out.println("  intent     : " + r.intent());
        if (r.pipelineId() != null) System.out.println("  pipelineId : " + r.pipelineId());
        if (r.pipelineName() != null) System.out.println("  name       : " + r.pipelineName());
        if (r.route() != null) System.out.println("  route      : " + r.route());
        if (r.steps() != null && !r.steps().isEmpty()) {
            System.out.println("  steps:");
            int i = 0;
            for (PlanStepDto s : r.steps()) {
                i++;
                System.out.printf("    %d. %s -> %s (%s)%n", i, s.stepId(), s.agentId(), s.action());
            }
        }
        if (r.clarifyQuestions() != null && !r.clarifyQuestions().isEmpty()) {
            System.out.println("  clarify    : " + String.join("；", r.clarifyQuestions()));
        }
        if (r.error() != null) System.out.println("  error      : " + r.error());
    }

    private void listAgentCards() {
        var cards = orchestratorTestService.listAgentCards();
        if (cards.isEmpty()) {
            System.out.println("  (AgentCardRegistry 为空，确认 Agent 已启动注册)");
            return;
        }
        System.out.printf("  共 %d 个 AgentCard:%n", cards.size());
        for (var c : cards) {
            System.out.printf("  %-28s %-12s outputs=%s%n",
                    c.agentId(), truncate(c.displayName(), 10),
                    c.outputArtifactKinds());
        }
    }

    private void listAiServerTools() {
        var tools = orchestratorTestService.listAiServerTools();
        System.out.println("  已封装 ai-server 工具:");
        for (var t : tools) {
            System.out.println("  - " + t.name() + ": " + truncate(t.description(), 60));
        }
    }

    private void handleCapability(String args) {
        if (args.isBlank()) {
            System.out.println("用法: capability <名称> [JSON参数]");
            System.out.println("示例: capability deep_solve {\"question\":\"什么是闭包\"}");
            return;
        }
        int jsonStart = args.indexOf('{');
        String name = jsonStart > 0 ? args.substring(0, jsonStart).trim() : args.trim();
        String json = jsonStart > 0 ? args.substring(jsonStart).trim() : "{}";
        var result = orchestratorTestService.testCapability(name,
                com.lqragent.backend.orchestrator.test.OrchestratorTestSupport.parseJsonArgs(json));
        if (result.success()) {
            System.out.println("[OK] " + result.capability() + " (" + result.durationMs() + "ms)");
            System.out.println(truncate(result.result(), 500));
        } else {
            System.out.println("[失败] " + result.error());
        }
    }

    private void handleLearningLoop(String args) {
        String[] p = args.split("\\s+");
        if (p.length < 2) {
            System.out.println("用法: loop <questionId> <score> [correct=true|false]");
            System.out.println("示例: loop 1 60 false");
            return;
        }
        long questionId = Long.parseLong(p[0]);
        int score = Integer.parseInt(p[1]);
        boolean correct = p.length >= 3 && "true".equalsIgnoreCase(p[2]);
        System.out.println("[loop] learning_loop questionId=" + questionId + " score=" + score);
        var r = orchestratorTestService.runLearningLoop(1L, questionId, "测试答案", correct, score);
        System.out.println("  expected: " + r.expectedStepIds());
        System.out.println("  aligned : " + r.stepsAligned());
        if (r.stepResults() != null) {
            for (StepResultDto s : r.stepResults()) {
                System.out.printf("  [%s] %s -> %s%n",
                        s.success() ? "OK" : "FAIL", s.stepId(), s.agentId());
            }
        }
        System.out.println(r.success() ? "[完成]" : "[失败] " + r.error());
    }

    private void handleIntentSuite() {
        System.out.println("[intent] 运行意图回归测试集...");
        IntentSuiteResult r = orchestratorTestService.runIntentSuite("1");
        System.out.printf("  结果: %d/%d 通过 (%.0f%%) 耗时 %dms%n",
                r.passed(), r.total(),
                r.total() > 0 ? r.passed() * 100.0 / r.total() : 0,
                r.durationMs());
        for (IntentCaseResult c : r.cases()) {
            String mark = c.passed() ? "OK" : "FAIL";
            System.out.printf("  [%s] %s%n", mark, c.input());
            if (!c.passed()) {
                System.out.printf("       期望 type=%s agents=%s | 实际 type=%s agents=%s%n",
                        c.expectedPlanType(), c.expectedAgentIds(),
                        c.actualPlanType(), c.actualAgentIds());
            }
        }
    }

    private void handlePipelineTaskStatus(String taskId) {
        if (taskId.isBlank()) {
            orchestratorTestService.getLatestPipelineTask(1L).ifPresentOrElse(
                    t -> printPipelineTask(t),
                    () -> System.out.println("  无 Pipeline 任务记录")
            );
            return;
        }
        orchestratorTestService.getPipelineTaskStatus(taskId).ifPresentOrElse(
                this::printPipelineTask,
                () -> System.out.println("  任务不存在: " + taskId)
        );
    }

    private void handleRetry(String taskId) {
        if (taskId.isBlank()) {
            System.out.println("用法: retry <taskId>");
            return;
        }
        System.out.println("[Retry] 从失败步骤重试 taskId=" + taskId + " ...");
        orchestratorTestService.retryPipelineTask(taskId, 1L, true).ifPresentOrElse(
                t -> {
                    printPipelineTask(t);
                    System.out.println("  重试完成，状态: " + t.status());
                },
                () -> System.out.println("  重试失败：任务不存在、非 FAILED 状态或无 failed_step")
        );
    }

    private void printPipelineTask(com.lqragent.backend.orchestrator.test.dto.OrchestratorTestDtos.PipelineTaskStatusDto t) {
        System.out.println("  taskId     : " + t.taskId());
        System.out.println("  status     : " + t.status());
        System.out.println("  pipeline   : " + t.pipelineName());
        System.out.println("  goal       : " + truncate(t.goal(), 80));
        System.out.println("  progress   : " + t.completedSteps() + "/" + t.stepCount());
        System.out.println("  current    : " + t.currentStep());
        if (t.failedStep() != null && !t.failedStep().isBlank()) {
            System.out.println("  failedStep : " + t.failedStep());
        }
        if (t.errorMessage() != null && !t.errorMessage().isBlank()) {
            System.out.println("  error      : " + t.errorMessage());
        }
    }

    private void printApiHelp() {
        System.out.println("""
                --- Orchestrator 测试 API（免认证 /api/test）---
                  POST /api/test/plan             仅意图规划
                  POST /api/test/pipeline         同步执行 Pipeline
                  POST /api/test/agent            完整路由（含 plan 详情）
                  GET  /api/test/agent-cards      Agent 能力目录
                  GET  /api/test/aiserver-tools   ai-server 工具列表
                  POST /api/test/capability/{name} 测试 ai-server capability
                  POST /api/test/learning-loop    学习闭环 Pipeline
                  POST /api/test/intent-suite     意图回归测试
                  GET  /api/test/learner-context  学习者上下文摘要
                  GET  /api/test/pipeline-task/latest
                  POST /api/test/pipeline-task/{taskId}/retry
                --- 其他 REST ---
                  POST /api/pipeline/tasks/{taskId}/retry  重试失败任务（需 JWT）
                  POST /api/auth/login            登录
                  POST /admin/agent-test          管理后台 Agent 测试（需 JWT）
                --- 常用配置键 ---
                  ai-server.base-url
                  ai-server.use-agentic-pipeline  (false=Java Orchestrator 主路径)
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
                  help / h              显示帮助
                  status / s            服务与 Orchestrator 概览
                  plan <消息>           仅意图规划（看 TaskPlan 步骤）
                  pipeline <消息>       同步执行完整 Pipeline
                  agents                列出 AgentCard 能力目录
                  tools                 列出 ai-server 封装工具
                  capability <名> [json] 测试 ai-server capability
                  loop <qid> <score> [true|false]  测试 learning_loop
                  intent                意图回归测试集
                  pipeline-status [taskId]  查看 Pipeline 任务状态
                  retry <taskId>            从失败步骤断点重试
                  config / c            列出全部配置
                  set <键> <值>         保存配置到 sys_config
                  users / u             查看系统用户
                  upload list|stats|run 上传队列
                  ai ping               测试 AI Server
                  api                   查看 REST API 列表
                  sync                  同步测试账号
                  quit / q              退出控制台菜单
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
