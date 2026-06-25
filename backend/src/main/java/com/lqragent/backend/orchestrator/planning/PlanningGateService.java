package com.lqragent.backend.orchestrator.planning;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lqragent.backend.orchestrator.context.LearnerContextDto;
import com.lqragent.backend.orchestrator.pipeline.PipelineConfig;
import com.lqragent.backend.orchestrator.pipeline.PipelineTemplates;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 规划 Gate：对 PlanResult 做确定性后处理（不依赖 LLM 稳定性）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanningGateService {

    private final AppRuntimeConfig runtimeConfig;

    public PlanResult apply(PlanResult plan, String message, LearnerContextDto learnerContext, boolean skipG1) {
        if (!runtimeConfig.isPlanningGateEnabled()) {
            return plan;
        }
        if (message == null || message.isBlank()) {
            return plan;
        }

        String msg = message.trim();
        LearnerContextDto ctx = learnerContext != null ? learnerContext : LearnerContextDto.builder().build();

        if (IntentHeuristics.isExplicitFullLearningPlan(msg)) {
            log.info("[PlanningGate] G4 explicit full learning plan");
            return forceLearningPath(plan, PipelineTemplates.learningPath());
        }

        if (IntentHeuristics.hasSufficientLearningDetails(msg) && isLearningPathRelated(plan, msg)) {
            log.info("[PlanningGate] G3 sufficient message details → learning_path_core");
            return forceLearningPath(plan, PipelineTemplates.learningPathCore());
        }

        if (!IntentHeuristics.isVagueLearningIntent(msg)) {
            return maybeDowngradeLearningPath(plan, msg, ctx);
        }

        if (!skipG1 && ctx.isEmpty() && !IntentHeuristics.hasSufficientLearningDetails(msg)) {
            log.info("[PlanningGate] G1 empty context + vague learning → clarify");
            return PlanResult.clarify(defaultClarifyQuestions(null), msg);
        }

        if (IntentHeuristics.hasSufficientLearningDetails(msg) || hasSufficientProfile(ctx)) {
            log.info("[PlanningGate] G3 sufficient details → learning_path_core");
            return forceLearningPath(plan, PipelineTemplates.learningPathCore());
        }

        if (!ctx.isEmpty()) {
            String topic = extractTopicHint(msg);
            if (topic != null) {
                log.info("[PlanningGate] G2 topic in message → topic clarify");
                return PlanResult.clarify(topicFocusedClarifyQuestions(msg), msg);
            }
            log.info("[PlanningGate] G2 partial profile → natural clarify");
            return PlanResult.clarify(profileAwareClarifyQuestions(ctx), msg);
        }

        log.info("[PlanningGate] G1 fallback clarify");
        return PlanResult.clarify(defaultClarifyQuestions(null), msg);
    }

    private PlanResult maybeDowngradeLearningPath(PlanResult plan, String message, LearnerContextDto ctx) {
        if (!plan.isPipeline() || plan.pipelineConfig() == null) {
            return plan;
        }
        String pipelineId = plan.pipelineConfig().getPipelineId();
        if (!"learning_path".equals(pipelineId)) {
            return plan;
        }
        if (IntentHeuristics.isExplicitFullLearningPlan(message)) {
            return plan;
        }
        if (runtimeConfig.isPlanningGatePreferCorePath()
                && (hasSufficientProfile(ctx) || IntentHeuristics.hasSufficientLearningDetails(message))) {
            log.info("[PlanningGate] downgrade learning_path → learning_path_core");
            return forceLearningPath(plan, PipelineTemplates.learningPathCore());
        }
        return plan;
    }

    private PlanResult forceLearningPath(PlanResult plan, PipelineConfig config) {
        return PlanResult.pipeline(config, config.getSteps());
    }

    private boolean isLearningPathRelated(PlanResult plan, String msg) {
        if (plan.isSimple() && plan.intent() == PlanIntent.LEARNING_PATH) {
            return true;
        }
        if (plan.isPipeline() && plan.pipelineConfig() != null) {
            String id = plan.pipelineConfig().getPipelineId();
            if (id != null && id.startsWith("learning_path")) {
                return true;
            }
        }
        return IntentHeuristics.isVagueLearningIntent(msg)
                || containsAny(msg, "想学", "帮我学", "学习路径", "学习计划", "入门", "规划");
    }

    private boolean hasSufficientProfile(LearnerContextDto ctx) {
        if (ctx == null || ctx.isEmpty()) {
            return false;
        }
        String profile = ctx.getProfileSummary() != null ? ctx.getProfileSummary() : "";
        boolean hasGoal = containsAny(profile, "目标", "方向", "想学", "计划", "Python", "Java", "数据分析");
        boolean hasLevel = containsAny(profile, "零基础", "初学", "基础", "进阶", "熟练");
        boolean hasTime = profile.matches(".*\\d+\\s*(个月|月|周|天).*");
        return (hasGoal && hasLevel) || (hasGoal && hasTime) || (hasLevel && hasTime);
    }

    private List<String> defaultClarifyQuestions(LearnerContextDto ctx) {
        List<String> qs = new ArrayList<>();
        qs.add("这次学习，你最想达成什么？（比如入门、做项目、备考）");
        qs.add("你现在的基础怎么样？（完全零基础 / 学过一点 / 有项目经验）");
        qs.add("打算学多久？（比如 4 周、3 个月）");
        return qs;
    }

    private List<String> profileAwareClarifyQuestions(LearnerContextDto ctx) {
        List<String> qs = new ArrayList<>();
        String goalHint = extractGoalFromProfile(ctx.getProfileSummary());
        if (goalHint != null) {
            qs.add("你之前提到过「" + goalHint + "」，这次还是继续这个方向，还是有新的打算？");
        } else {
            qs.add("你希望这次学习的重点放在哪里？");
        }
        qs.add("方便说下你现在的基础吗？每天或每周大概能投入多少时间？");
        qs.add("你希望在多久之内看到阶段性成果？（比如 1 个月、3 个月）");
        return qs;
    }

    /** 用户消息里已点明主题时的追问（口语化，不引用画像字段） */
    private List<String> topicFocusedClarifyQuestions(String message) {
        List<String> qs = new ArrayList<>();
        String topic = extractTopicHint(message);
        if (topic != null) {
            qs.add("学 " + topic + " 的话，你更想达到什么程度？（能写小项目 / 求职 / 先入门了解一下）");
        } else {
            qs.add("这次学习，你最想达成什么？（比如入门、做项目、备考）");
        }
        qs.add("你现在的基础怎么样？（完全零基础 / 学过一点 / 有项目经验）");
        qs.add("打算学多久？（比如 4 周、3 个月）");
        return qs;
    }

    /** 从画像摘要中提取可读的学习目标短语（不暴露原始字段） */
    private static String extractGoalFromProfile(String profileSummary) {
        if (profileSummary == null || profileSummary.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("学习目标[:：]\\s*([^;；\\n]+)")
                .matcher(profileSummary);
        if (m.find()) {
            String goal = m.group(1).trim();
            return goal.length() > 40 ? goal.substring(0, 40) + "…" : goal;
        }
        return null;
    }

    private static String extractTopicHint(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String m = message.toLowerCase().replaceAll("\\s+", "");
        String[][] topics = {
                {"python", "Python"},
                {"pyhton", "Python"},
                {"java", "Java"},
                {"javascript", "JavaScript"},
                {"数据分析", "数据分析"},
                {"机器学习", "机器学习"},
                {"web", "Web 开发"},
                {"django", "Django"},
                {"前端", "前端开发"},
                {"后端", "后端开发"},
                {"react", "React"},
                {"vue", "Vue"},
        };
        for (String[] pair : topics) {
            if (m.contains(pair[0])) {
                return pair[1];
            }
        }
        return null;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
