package com.lqragent.backend.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "WebSocket 聊天协议说明")
public class ChatProtocolDto {

    @Schema(description = "WebSocket 端点", example = "/ws/chat?token=<JWT>")
    private String endpoint;

    @Schema(description = "客户端→服务端消息格式")
    private ClientMessage clientMessage;

    @Schema(description = "服务端→客户端事件类型列表")
    private List<ServerEvent> serverEvents;

    @Data
    @Builder
    @Schema(description = "客户端发送的消息")
    public static class ClientMessage {
        @Schema(example = "message")
        private String type;
        @Schema(example = "我想学 Python 装饰器")
        private String content;
        @Schema(example = "uuid-可选，首次为空则自动创建会话")
        private String sessionId;
    }

    @Data
    @Builder
    @Schema(description = "服务端推送的事件")
    public static class ServerEvent {
        @Schema(description = "事件类型", example = "chunk")
        private String type;
        @Schema(description = "触发时机", example = "AI 流式输出每个 token")
        private String when;
        @Schema(description = "关键字段", example = "content: 文本片段 | label, agent, status | kind, payload")
        private Map<String, String> fields;
    }
}
