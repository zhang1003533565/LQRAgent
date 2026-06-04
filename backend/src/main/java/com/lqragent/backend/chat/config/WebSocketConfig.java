package com.lqragent.backend.chat.config;

import com.lqragent.backend.chat.handler.ChatWebSocketHandler;
import com.lqragent.backend.orchestrator.OrchestratorWebSocketHandler;
import com.lqragent.backend.chat.handler.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 /ws/chat WebSocket 端点，握手阶段校验 JWT。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final OrchestratorWebSocketHandler orchestratorWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orchestratorWebSocketHandler, "/ws/orchestrator")
                .setAllowedOrigins("*");

        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
