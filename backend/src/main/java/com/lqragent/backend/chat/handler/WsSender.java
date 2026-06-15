package com.lqragent.backend.chat.handler;

import org.springframework.web.socket.WebSocketSession;

/**
 * WebSocket 消息发送接口
 * 解耦 Dispatcher 和具体的 WebSocket 发送实现
 */
@FunctionalInterface
public interface WsSender {
    void sendEvent(WebSocketSession session, String type, String content);
}
