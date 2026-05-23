package com.lqragent.backend.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "聊天消息记录")
public class ChatMessageDto {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "发送方", example = "USER")
    private String sender;

    @Schema(description = "内容类型", example = "TEXT")
    private String contentType;

    @Schema(description = "消息正文")
    private String body;

    @Schema(description = "发送时间")
    private LocalDateTime createdAt;
}
