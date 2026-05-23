package com.lqragent.backend.chat.controller;

import com.lqragent.backend.chat.dto.ChatMessageDto;
import com.lqragent.backend.chat.dto.ChatProtocolDto;
import com.lqragent.backend.chat.dto.ChatSessionDto;
import com.lqragent.backend.chat.entity.ChatMessage;
import com.lqragent.backend.chat.entity.ChatSession;
import com.lqragent.backend.chat.repository.ChatMessageRepository;
import com.lqragent.backend.chat.service.ChatSessionService;
import com.lqragent.backend.common.dto.ApiResponse;
import com.lqragent.backend.user.entity.User;
import com.lqragent.backend.user.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "聊天", description = "WebSocket 聊天协议说明与会话历史查询")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final CurrentUserService currentUserService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageRepository chatMessageRepository;

    @Operation(
            summary = "WebSocket 聊天协议说明",
            description = "返回 /ws/chat 端点的事件类型、消息格式和前端对接说明。" +
                    " WebSocket 连接时在 query param 中传入 JWT token 完成认证。" +
                    " 客户端连接后发送 {type:\"message\", content:\"...\", session_id:\"...\"}，" +
                    " 服务端依次推送 session_created → agent_step → chunk... → done/error 事件。"
    )
    @GetMapping("/protocol")
    public ApiResponse<ChatProtocolDto> getProtocol() {
        ChatProtocolDto protocol = ChatProtocolDto.builder()
                .endpoint("/ws/chat?token=<JWT>")
                .clientMessage(ChatProtocolDto.ClientMessage.builder()
                        .type("message")
                        .content("我想学 Python 装饰器")
                        .sessionId("uuid-可选，首次为空则自动创建会话")
                        .build())
                .serverEvents(List.of(
                        ChatProtocolDto.ServerEvent.builder()
                                .type("session_created")
                                .when("首次发送消息时，服务端创建聊天会话后推送")
                                .fields(Map.of("session_id", "新建的会话 UUID", "title", "会话标题"))
                                .build(),
                        ChatProtocolDto.ServerEvent.builder()
                                .type("agent_step")
                                .when("每个智能体开始/完成/失败时推送，前端渲染 AgentTimeline")
                                .fields(Map.of(
                                        "agent", "智能体标识：qa_agent/path_planner/resource_facade 等",
                                        "label", "人可读步骤名称",
                                        "status", "running | done | failed",
                                        "detail", "失败时的原因说明（可选）"))
                                .build(),
                        ChatProtocolDto.ServerEvent.builder()
                                .type("chunk")
                                .when("AI 流式输出每个 token 时推送，前端逐字追加到助手消息尾部")
                                .fields(Map.of("content", "文本片段"))
                                .build(),
                        ChatProtocolDto.ServerEvent.builder()
                                .type("done")
                                .when("本轮对话结束，前端停止 streaming 动画")
                                .fields(Map.of("session_id", "当前会话 UUID"))
                                .build(),
                        ChatProtocolDto.ServerEvent.builder()
                                .type("error")
                                .when("发生错误时推送，前端 Toast 提示")
                                .fields(Map.of("content", "错误描述"))
                                .build(),
                        ChatProtocolDto.ServerEvent.builder()
                                .type("artifact")
                                .when("智能体产出结构化结果（学习路径/讲义/题目/图片等）")
                                .fields(Map.of(
                                        "kind", "learning_path | lesson | quiz | code_case | media_image | media_video | multi_card",
                                        "payload", "kind 对应的结构化 JSON"))
                                .build(),
                        ChatProtocolDto.ServerEvent.builder()
                                .type("profile_patch")
                                .when("画像数据增量更新时推送，前端合并到左侧画像卡片")
                                .fields(Map.of("payload", "Partial<ProfileSummary> JSON"))
                                .build()
                ))
                .build();
        return ApiResponse.ok(protocol);
    }

    @Operation(summary = "查询当前用户的聊天会话列表", description = "按最近更新时间倒序，返回每个会话的消息数量")
    @GetMapping("/sessions")
    public ApiResponse<List<ChatSessionDto>> getSessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = currentUserService.requireUser(userDetails);
        List<ChatSession> sessions = chatSessionService.findByUserId(user.getId());
        List<ChatSessionDto> dtos = sessions.stream()
                .map(s -> {
                    List<ChatMessage> msgs = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(s.getId());
                    return ChatSessionDto.builder()
                            .id(s.getId())
                            .title(s.getTitle())
                            .createdAt(s.getCreatedAt())
                            .updatedAt(s.getUpdatedAt())
                            .messageCount(msgs.size())
                            .build();
                })
                .toList();
        return ApiResponse.ok(dtos);
    }

    @Operation(summary = "查询某次会话的消息记录", description = "按时间正序返回所有 USER/AI 消息，用于历史回放")
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ChatMessageDto>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @Parameter(description = "会话ID") @PathVariable String sessionId) {
        currentUserService.requireUser(userDetails);
        List<ChatMessage> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatMessageDto> dtos = messages.stream()
                .map(m -> ChatMessageDto.builder()
                        .id(m.getId())
                        .sender(m.getSender().name())
                        .contentType(m.getContentType().name())
                        .body(m.getBody())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
        return ApiResponse.ok(dtos);
    }
}
