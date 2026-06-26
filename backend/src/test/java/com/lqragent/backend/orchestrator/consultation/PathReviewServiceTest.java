package com.lqragent.backend.orchestrator.consultation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.lqragent.backend.agents.path.dto.LearningPathDto;

class PathReviewServiceTest {

    private final PathReviewService service = new PathReviewService(null, null);

    @Test
    void heuristicReview_revisesWhenBeginnerGetsAdvancedPath() {
        LearningPathDto path = LearningPathDto.builder()
                .nodes(List.of(
                        LearningPathDto.PathNode.builder().title("高级项目实战").order(0).kpId("kp1").build(),
                        LearningPathDto.PathNode.builder().title("进阶").order(1).kpId("kp2").build()))
                .build();

        PathReviewDecision decision = service.review("零基础初学者", path, "学 Python");

        assertFalse(decision.approved());
    }

    @Test
    void heuristicReview_approvesSimplePath() {
        LearningPathDto path = LearningPathDto.builder()
                .nodes(List.of(
                        LearningPathDto.PathNode.builder().title("语法入门").order(0).kpId("kp1").build()))
                .build();

        PathReviewDecision decision = service.review("有基础", path, "学 Python");

        assertTrue(decision.approved());
    }
}
