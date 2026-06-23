package com.lqragent.backend.chat.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天 WebSocket 异步处理线程池（避免在 WS 线程上同步调用 LLM / Pipeline）
 */
@Configuration
public class ChatAsyncConfig {

    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatWsExecutor() {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "chat-ws-worker");
            t.setDaemon(true);
            return t;
        };
        return Executors.newFixedThreadPool(4, factory);
    }
}
