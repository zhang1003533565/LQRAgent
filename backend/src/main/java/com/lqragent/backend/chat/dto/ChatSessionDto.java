package com.lqragent.backend.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "聊天会话摘要")
public class ChatSessionDto {

    @Schema(description = "会话ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String id;

    @Schema(description = "会话标题", example = "想学 Python 装饰器")
    private String title;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "最近更新时间")
    private LocalDateTime updatedAt;

    @Schema(description = "消息数量")
    private int messageCount;
}
