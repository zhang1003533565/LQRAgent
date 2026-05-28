package com.lqragent.backend.agents.effect_assessment.service;

import com.lqragent.backend.agents.learning_path.entity.LearningPath;
import com.lqragent.backend.agents.learning_path.entity.LearningPathStep;
import com.lqragent.backend.agents.learning_path.repository.LearningPathRepository;
import com.lqragent.backend.agents.learning_path.repository.LearningPathStepRepository;
import com.lqragent.backend.quiz.entity.StudyBehavior;
import com.lqragent.backend.quiz.repository.StudyBehaviorRepository;
import com.lqragent.backend.agents.shared.llm.LlmContentGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 效果评估智能体。
 *
 * 功能：
 * - 低分插入复习节点
 * - LLM 分析薄弱点（从行为+答题数据）
 * - 触发路径调整
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EffectAssessmentService {

    private final LearningPathRepository learningPathRepo;
    private final LearningPathStepRepository stepRepo;
    private final StudyBehaviorRepository behaviorRepo;
    private final LlmContentGenerator llmGenerator;

    /**
     * 答题后评估，低分时插入复习节点。
     */
    @Transactional
    public void evaluateQuizResult(Long userId, String kpId, int score, boolean correct) {
        if (correct && score >= 60) return;

        Optional<LearningPath> activePath = learningPathRepo
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");
        if (activePath.isEmpty()) return;

        Long pathId = activePath.get().getId();
        List<LearningPathStep> steps = stepRepo.findByPathIdOrderByStepOrder(pathId);

        boolean alreadyHasKp = steps.stream().anyMatch(s -> s.getKpId().equals(kpId));
        if (!alreadyHasKp) {
            int maxOrder = steps.stream().mapToInt(LearningPathStep::getStepOrder).max().orElse(0);
            stepRepo.save(LearningPathStep.builder()
                    .pathId(pathId).kpId(kpId)
                    .stepOrder(maxOrder + 1).completed(false).status("PENDING")
                    .build());
            log.info("[EffectAssessment] 插入复习节点: userId={}, kpId={}", userId, kpId);
        }
    }

    /**
     * LLM 分析薄弱点（基于行为和答题数据）。
     * @return 分析文本，包含薄弱知识点列表 + 建议
     */
    public String analyzeWeakness(Long userId) {
        // 获取用户最近的行为数据
        List<StudyBehavior> behaviors = behaviorRepo.findByUserIdOrderByCreatedAtDesc(userId);
        if (behaviors.isEmpty()) {
            return "暂无足够的学习行为数据进行分析。";
        }

        String behaviorSummary = behaviors.stream()
                .limit(50)
                .map(b -> String.format("[%s] %s %ds", b.getAction(), b.getKpId() != null ? b.getKpId() : "-", b.getDurationSec() != null ? b.getDurationSec() : 0))
                .collect(Collectors.joining("\n"));

        String result = llmGenerator.generate("weakness_analysis",
                "学生学习行为分析",
                "以下是学生的学习行为记录，请分析薄弱点：\n" + behaviorSummary);
        return result != null ? result : "分析暂不可用（LLM 未配置）";
    }
}
