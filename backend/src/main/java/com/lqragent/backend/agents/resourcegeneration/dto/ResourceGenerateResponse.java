package com.lqragent.backend.agents.content.summary.lessongeneration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "资源生成响应")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceGenerateResponse {

    @Schema(description = "资源ID")
    private Long resourceId;

    @Schema(description = "知识点ID")
    private String kpId;

    @Schema(description = "资源类型")
    private String resourceType;

    @Schema(description = "资源标题")
    private String title;

    @Schema(description = "文本内容（Markdown）")
    private String content;

    @Schema(description = "媒体访问URL（仅媒体类资源）")
    private String mediaUrl;

    @Schema(description = "已存在的历史资源数量")
    private int existingCount;
}
