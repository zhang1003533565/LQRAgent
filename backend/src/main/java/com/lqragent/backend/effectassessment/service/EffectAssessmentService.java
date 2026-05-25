package com.lqragent.backend.effectassessment.service;

import com.lqragent.backend.learningpath.entity.LearningPath;
import com.lqragent.backend.learningpath.entity.LearningPathStep;
import com.lqragent.backend.learningpath.repository.LearningPathRepository;
import com.lqragent.backend.learningpath.repository.LearningPathStepRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 效果评估智能体。
 * 根据答题结果，动态调整学习路径：
 * - 低分（<60）：在路径中插入复习节点
 * - 连续错误：标记该 KP 需额外练习
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectAssessmentService {

    private final LearningPathRepository learningPathRepo;
    private final LearningPathStepRepository stepRepo;

    /**
     * 答题后评估，低分时插入复习节点。
     *
     * @param userId  学生ID
     * @param kpId    知识点ID
     * @param score   得分 0-100
     * @param correct 是否正确
     */
    @Transactional
    public void evaluateQuizResult(Long userId, String kpId, int score, boolean correct) {
        if (correct && score >= 60) {
            // 合格，不做调整
            return;
        }

        // 查找用户当前 ACTIVE 路径
        Optional<LearningPath> activePath = learningPathRepo
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");

        if (activePath.isEmpty()) {
            log.info("[EffectAssessment] userId={} 无活跃路径，跳过调整", userId);
            return;
        }

        Long pathId = activePath.get().getId();
        List<LearningPathStep> steps = stepRepo.findByPathIdOrderByStepOrder(pathId);

        // 检查是否已有该 KP 的步骤
        boolean alreadyHasKp = steps.stream().anyMatch(s -> s.getKpId().equals(kpId));
        if (!alreadyHasKp) {
            // 插入复习节点到路径末尾
            int maxOrder = steps.stream()
                    .mapToInt(LearningPathStep::getStepOrder)
                    .max().orElse(0);

            LearningPathStep reviewStep = LearningPathStep.builder()
                    .pathId(pathId)
                    .kpId(kpId)
                    .stepOrder(maxOrder + 1)
                    .completed(false)
                    .status("PENDING")
                    .build();

            stepRepo.save(reviewStep);
            log.info("[EffectAssessment] 插入复习节点: userId={}, kpId={}, order={}", userId, kpId, maxOrder + 1);
        } else {
            log.info("[EffectAssessment] KP={} 已在路径中，标记为待复习（当前可扩展）", kpId);
        }
    }
}
