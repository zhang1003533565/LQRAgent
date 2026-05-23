package com.lqragent.backend.chat.handler;

import com.lqragent.backend.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器：从 query param 提取 JWT token 并校验，
 * 将用户信息写入 WebSocket session attributes。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        if (query == null || !query.contains("token=")) {
            log.warn("[WS] handshake rejected: missing token");
            return false;
        }

        String token = extractQueryParam(query, "token");
        if (token == null || !jwtUtil.isValid(token)) {
            log.warn("[WS] handshake rejected: invalid token");
            return false;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            attributes.put("userId", claims.get("userId", Long.class));
            attributes.put("username", claims.getSubject());
            attributes.put("role", claims.get("role", String.class));
            log.info("[WS] handshake OK: userId={}, username={}",
                    attributes.get("userId"), attributes.get("username"));
            return true;
        } catch (Exception e) {
            log.warn("[WS] handshake rejected: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String extractQueryParam(String query, String key) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && key.equals(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }
}
