package com.lqragent.backend.systemconfig;

import com.lqragent.backend.systemconfig.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 首次启动时将 application.properties 中的关键项写入 sys_config（仅当库中不存在时）。
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class SysConfigSeeder implements ApplicationRunner {

    private final SysConfigService sysConfigService;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        seedIfAbsent(ConfigKeys.AI_SERVER_BASE_URL, "AI 服务 HTTP 根地址");
        seedIfAbsent(ConfigKeys.AI_SERVER_WS_URL, "AI 服务 WebSocket 地址");
        seedIfAbsent(ConfigKeys.AI_SERVER_AUTO_START, "启动后端时是否自动拉起 AI Server");
        seedIfAbsent(ConfigKeys.UPLOAD_WORKER_INTERVAL_MS, "上传队列轮询间隔(毫秒)");
        seedIfAbsent(ConfigKeys.UPLOAD_MAX_CONCURRENT, "上传队列最大并发数");

        seedDefault(ConfigKeys.LLM_BINDING, "openai", "大模型提供商：openai/deepseek/dashscope 等");
        seedDefault(ConfigKeys.LLM_MODEL, "gpt-4o-mini", "大模型名称");
        seedDefault(ConfigKeys.LLM_HOST, "https://api.openai.com/v1", "大模型 API 根地址");
        seedDefault(ConfigKeys.EMBEDDING_BINDING, "openai", "嵌入模型提供商");
        seedDefault(ConfigKeys.EMBEDDING_MODEL, "text-embedding-3-large", "嵌入模型名称");
        seedDefault(ConfigKeys.EMBEDDING_HOST, "https://api.openai.com/v1/embeddings", "嵌入 API 完整 URL");
    }

    private void seedDefault(String key, String defaultValue, String remark) {
        if (sysConfigService.findByKey(key).isPresent()) {
            return;
        }
        sysConfigService.upsert(key, defaultValue, remark);
        log.info("[SysConfigSeeder] 已初始化默认配置：{}", key);
    }

    private void seedIfAbsent(String key, String remark) {
        if (sysConfigService.findByKey(key).isPresent()) {
            return;
        }
        String value = environment.getProperty(key);
        if (value == null || value.isBlank()) {
            return;
        }
        sysConfigService.upsert(key, value, remark);
        log.info("[SysConfigSeeder] 已初始化配置：{}", key);
    }
}
