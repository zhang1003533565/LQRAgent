package com.lqragent.backend.learningpath.service;

import com.lqragent.backend.learningpath.dto.LearningPathDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学习路径规划服务。
 * 流程：图谱算候选路径 → 画像决定节奏 → 大模型生成自然语言计划。
 *
 * 当前为骨架实现，返回占位数据，后续接入知识图谱和 AiServerClient。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningPathService {

    public LearningPathDto generatePath(String goal, String currentKpId) {
        log.info("[LearningPath] generatePath: goal={}, currentKpId={}", goal, currentKpId);

        // TODO: 1. 调用 KnowledgeGraphService.findLearningPath(currentKpId, targetKpId)
        // TODO: 2. 调用 LearnerProfileService 获取画像，调整节奏
        // TODO: 3. 调用 AiServerClient 让大模型生成自然语言计划

        return LearningPathDto.builder()
                .goal(goal)
                .nodes(List.of(
                        LearningPathDto.PathNode.builder()
                                .kpId("kp_placeholder_1")
                                .title("占位知识点 1")
                                .description("待接入知识图谱后替换")
                                .order(1)
                                .completed(false)
                                .build()
                ))
                .planDescription("学习路径生成中，待接入知识图谱和 AI 服务后完善。")
                .build();
    }
}
