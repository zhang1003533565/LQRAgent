package com.lqragent.backend.orchestrator.consultation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lqragent.backend.agents.path.dto.LearningPathDto;

class PathConsultationAdjusterTest {

    @Test
    void trimsLeadingBeginnerNodesForPartialBaseLearner() {
        LearningPathDto path = LearningPathDto.builder()
                .pathId(1L)
                .goal("学 Python")
                .nodes(List.of(
                        LearningPathDto.PathNode.builder().title("Python简介与环境搭建").order(0).kpId("a").build(),
                        LearningPathDto.PathNode.builder().title("变量与数据类型").order(1).kpId("b").build(),
                        LearningPathDto.PathNode.builder().title("装饰器").order(2).kpId("c").build()))
                .build();

        LearningPathDto adjusted = PathConsultationAdjuster.apply(
                path,
                "有一点基础",
                "我想学python\n补充信息：有一点基础",
                "跳过基础入门章节，增加进阶内容");

        assertTrue(adjusted.getNodes().size() < path.getNodes().size());
        assertEquals("装饰器", adjusted.getNodes().get(0).getTitle());
    }

    @Test
    void noTrimWhenFeedbackDoesNotAsk() {
        LearningPathDto path = LearningPathDto.builder()
                .nodes(List.of(
                        LearningPathDto.PathNode.builder().title("Python简介").order(0).kpId("a").build()))
                .build();

        LearningPathDto adjusted = PathConsultationAdjuster.apply(
                path, "零基础", "学 Python", "路径结构清晰");

        assertEquals(1, adjusted.getNodes().size());
    }
}
