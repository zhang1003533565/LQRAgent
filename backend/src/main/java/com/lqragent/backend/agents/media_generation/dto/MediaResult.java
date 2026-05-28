package com.lqragent.backend.agents.media_generation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "媒体生成结果")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaResult {

    @Schema(description = "资源ID")
    private Long resourceId;

    @Schema(description = "知识点ID")
    private String kpId;

    @Schema(description = "媒体访问URL")
    private String mediaUrl;

    @Schema(description = "MIME 类型")
    private String mediaMime;

    @Schema(description = "生成使用的提示词")
    private String prompt;

    @Schema(description = "是否为新生成")
    private boolean newlyCreated;
}
