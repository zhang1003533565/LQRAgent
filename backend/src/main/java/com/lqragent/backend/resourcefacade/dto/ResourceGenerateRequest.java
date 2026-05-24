package com.lqragent.backend.resourcefacade.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "资源生成请求")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceGenerateRequest {

    @Schema(description = "知识点ID", example = "kp_decorator")
    private String kpId;

    @Schema(description = "资源类型：LESSON/QUIZ/CODE_CASE/ILLUSTRATION/SUMMARY", example = "LESSON")
    private String resourceType;

    @Schema(description = "可选：覆盖默认提示词")
    private String customPrompt;
}
