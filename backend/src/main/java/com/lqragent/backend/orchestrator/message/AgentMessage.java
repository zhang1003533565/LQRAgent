package com.lqragent.backend.orchestrator.message;

import lombok.*;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * 智能体通信消息（参考 FIPA ACL + Google A2A 规范）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMessage {
    private String id;
    private String taskId;
    private Performative performative;
    private String sender;
    private String receiver;
    private Map<String, Object> content;
    private String conversationId;
    private String inReplyTo;
    private long timestamp;

    public static AgentMessage request(String taskId, String sender, String receiver,
                                        Map<String, Object> content) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .performative(Performative.REQUEST)
                .sender(sender)
                .receiver(receiver)
                .content(content != null ? content : new HashMap<>())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentMessage inform(String taskId, String sender, String receiver,
                                       Map<String, Object> content) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .performative(Performative.INFORM)
                .sender(sender)
                .receiver(receiver)
                .content(content != null ? content : new HashMap<>())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentMessage progress(String taskId, String sender, String message) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .performative(Performative.PROGRESS)
                .sender(sender)
                .receiver("orchestrator")
                .content(Map.of("message", message))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static AgentMessage error(String taskId, String sender, String errorMsg) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .performative(Performative.ERROR)
                .sender(sender)
                .receiver("orchestrator")
                .content(Map.of("error", errorMsg))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Agent间协作请求：一个Agent请求另一个Agent协助
     */
    public static AgentMessage requestPeer(String taskId, String sender, String peerAgent,
                                            Map<String, Object> content) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .performative(Performative.REQUEST_PEER)
                .sender(sender)
                .receiver(peerAgent)
                .content(content != null ? content : new HashMap<>())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * Agent间协作响应
     */
    public static AgentMessage informPeer(String taskId, String sender, String peerAgent,
                                           Map<String, Object> content) {
        return AgentMessage.builder()
                .id(UUID.randomUUID().toString())
                .taskId(taskId)
                .performative(Performative.INFORM_PEER)
                .sender(sender)
                .receiver(peerAgent)
                .content(content != null ? content : new HashMap<>())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
