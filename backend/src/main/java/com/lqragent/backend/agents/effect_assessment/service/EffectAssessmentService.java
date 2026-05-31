package com.lqragent.backend.agents.effect_assessment.service;

import com.lqragent.backend.agents.learning_path.entity.LearningPath;
import com.lqragent.backend.agents.learning_path.entity.LearningPathStep;
import com.lqragent.backend.agents.learning_path.repository.LearningPathRepository;
import com.lqragent.backend.agents.learning_path.repository.LearningPathStepRepository;
import com.lqragent.backend.quiz.entity.StudyBehavior;
import com.lqragent.backend.quiz.repository.StudyBehaviorRepository;
import com.lqragent.backend.framework.llm.LlmContentGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
     * 答题后评估，低分时在当前学习位置之后插入复习节点。
     * 避免复习节点出现在已学完的节点之前。
     */
    @Transactional
    public void evaluateQuizResult(Long userId, String kpId, int score, boolean correct) {
        if (correct && score >= 60) return;

        Optional<LearningPath> activePath = learningPathRepo
                .findTopByUserIdAndStatusOrderByCreatedAtDesc(userId, "ACTIVE");
        if (activePath.isEmpty()) return;

        Long pathId = activePath.get().getId();
        List<LearningPathStep> steps = stepRepo.findByPathIdOrderByStepOrder(pathId);

        boolean alreadyHasKp = steps.stream().anyMatch(s ->
                s.getKpId().equals(kpId) && !Boolean.TRUE.equals(s.getCompleted()));
        if (alreadyHasKp) return;

        // 找到最后一个已完成的步骤，插入在其之后
        int insertAfterOrder = -1;
        for (LearningPathStep step : steps) {
            if (Boolean.TRUE.equals(step.getCompleted())) {
                insertAfterOrder = step.getStepOrder();
            }
        }

        // 找到插入点后面第一个未完成步骤的 order，复习节点插在它之前
        int newOrder = insertAfterOrder + 1;
        List<LearningPathStep> toShift = new ArrayList<>();
        for (LearningPathStep step : steps) {
            if (step.getStepOrder() >= newOrder) {
                toShift.add(step);
            }
        }
        // 后移已有步骤，腾出位置
        for (LearningPathStep step : toShift) {
            step.setStepOrder(step.getStepOrder() + 1);
            stepRepo.save(step);
        }

        stepRepo.save(LearningPathStep.builder()
                .pathId(pathId).kpId(kpId)
                .stepOrder(newOrder).completed(false).status("PENDING")
                .build());
        log.info("[EffectAssessment] 插入复习节点: userId={}, kpId={}, order={}", userId, kpId, newOrder);
    }

    /**
     * LLM 分析薄弱点（基于行为和答题数据）。
     * 异步执行，不阻塞用户答题请求。
     */
    @Async
    public void analyzeWeaknessAsync(Long userId) {
        try {
            String result = doAnalyzeWeakness(userId);
            log.info("[EffectAssessment] 薄弱点分析完成: userId={}, result={}", userId,
                    result.length() > 100 ? result.substring(0, 100) + "..." : result);
        } catch (Exception e) {
            log.warn("[EffectAssessment] 薄弱点分析失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * LLM 分析薄弱点（同步版本，供需要返回值的场景调用）。
     */
    public String analyzeWeakness(Long userId) {
        return doAnalyzeWeakness(userId);
    }

    private String doAnalyzeWeakness(Long userId) {
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
