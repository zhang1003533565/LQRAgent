package com.lqragent.backend.orchestrator.consultation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.lqragent.backend.agents.path.dto.LearningPathDto;
import com.lqragent.backend.orchestrator.planning.IntentHeuristics;

/**
 * Phase 2 收口：将 Difficulty revise 反馈落地到路径节点（裁剪入门段）。
 */
public final class PathConsultationAdjuster {

    private static final String[] BEGINNER_TITLE_KEYWORDS = {
            "简介", "环境", "搭建", "hello", "变量", "数据类型", "输入", "输出",
            "基础语法", "语法基础", "入门", "初识", "第一个程序"
    };

    private PathConsultationAdjuster() {}

    public static boolean shouldApplyRevision(String profileSummary, String goal, String feedback) {
        if (feedback == null || feedback.isBlank()) {
            return false;
        }
        String combined = safe(profileSummary) + " " + safe(goal) + " " + feedback;
        boolean learnerHasBase = IntentHeuristics.hasLevelHint(combined)
                || containsAny(combined.toLowerCase(Locale.ROOT), "intermediate", "进阶");
        boolean asksTrim = containsAny(feedback, "基础", "入门", "减少", "跳过", "合并", "简化", "后置", "偏易")
                || containsAny(feedback.toLowerCase(Locale.ROOT), "beginner", "too easy");
        return learnerHasBase && asksTrim;
    }

    public static LearningPathDto apply(LearningPathDto path, String profileSummary, String goal, String feedback) {
        if (path == null || path.getNodes() == null || path.getNodes().isEmpty()) {
            return path;
        }
        if (!shouldApplyRevision(profileSummary, goal, feedback)) {
            return path;
        }
        List<LearningPathDto.PathNode> trimmed = trimLeadingBeginnerNodes(path.getNodes(), 8);
        if (trimmed.size() >= path.getNodes().size()) {
            return path;
        }
        String planDescription = buildPlanDescription(trimmed);
        return LearningPathDto.builder()
                .pathId(path.getPathId())
                .goal(path.getGoal())
                .nodes(trimmed)
                .planDescription(planDescription)
                .build();
    }

    static List<LearningPathDto.PathNode> trimLeadingBeginnerNodes(
            List<LearningPathDto.PathNode> nodes, int maxSkip) {
        List<LearningPathDto.PathNode> result = new ArrayList<>(nodes);
        int skipped = 0;
        while (!result.isEmpty() && skipped < maxSkip && isBeginnerNode(result.get(0))) {
            result.remove(0);
            skipped++;
        }
        for (int i = 0; i < result.size(); i++) {
            result.set(i, result.get(i).toBuilder().order(i).build());
        }
        return result;
    }

    private static boolean isBeginnerNode(LearningPathDto.PathNode node) {
        String title = node.getTitle() != null ? node.getTitle().toLowerCase(Locale.ROOT) : "";
        String desc = node.getDescription() != null ? node.getDescription().toLowerCase(Locale.ROOT) : "";
        for (String kw : BEGINNER_TITLE_KEYWORDS) {
            if (title.contains(kw.toLowerCase(Locale.ROOT)) || desc.contains(kw.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String buildPlanDescription(List<LearningPathDto.PathNode> nodes) {
        if (nodes.isEmpty()) {
            return "暂无学习计划";
        }
        StringBuilder sb = new StringBuilder("学习路线：");
        for (int i = 0; i < nodes.size(); i++) {
            if (i > 0) {
                sb.append(" -> ");
            }
            sb.append(nodes.get(i).getTitle() != null ? nodes.get(i).getTitle() : nodes.get(i).getKpId());
        }
        return sb.toString();
    }

    private static String safe(String s) {
        return s != null ? s : "";
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
