package com.lqragent.backend.agents.learning_path.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Schema(description = "学习路径")
@Data
@Builder
public class LearningPathDto {

    @Schema(description = "路径 ID")
    private Long pathId;

    @Schema(description = "学习目标")
    private String goal;

    @Schema(description = "路径节点列表，按学习顺序排列")
    private List<PathNode> nodes;

    @Schema(description = "自然语言学习计划说明")
    private String planDescription;

    @Schema(description = "路径节点")
    @Data
    @Builder(toBuilder = true)
    public static class PathNode {

        @Schema(description = "知识点 ID")
        private String kpId;

        @Schema(description = "知识点标题")
        private String title;

        @Schema(description = "知识点描述")
        private String description;

        @Schema(description = "学习顺序，从 0 开始")
        private int order;

        @Schema(description = "是否已完成学习")
        private boolean completed;

        @Schema(description = "步骤状态：PENDING/ACTIVE/COMPLETED/SKIPPED")
        private String status;
    }
}
