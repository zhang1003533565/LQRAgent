package com.lqragent.backend.learningpath.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LearningPathDto {

    private String goal;

    /** 路径节点列表，按学习顺序排列 */
    private List<PathNode> nodes;

    /** 大模型生成的自然语言学习计划 */
    private String planDescription;

    @Data
    @Builder
    public static class PathNode {
        private String kpId;
        private String title;
        private String description;
        private int order;
        /** 该节点是否已完成 */
        private boolean completed;
    }
}
