package com.lqragent.backend.agents.learner_profile.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "画像更新请求（只传需要改的字段）")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfilePatchRequest {

    @Schema(description = "知识水平")
    private String knowledgeLevel;

    @Schema(description = "当前学习目标")
    private String learningGoal;

    @Schema(description = "认知风格")
    private String cognitiveStyle;

    @Schema(description = "常见错误（JSON数组）")
    private String commonErrors;

    @Schema(description = "学习节奏")
    private String learningPace;

    @Schema(description = "兴趣方向（JSON数组）")
    private String interestDirection;

    @Schema(description = "偏好资源类型")
    private String preferredResourceType;

    @Schema(description = "知识点掌握状态 JSON")
    private String topicMastery;
}
