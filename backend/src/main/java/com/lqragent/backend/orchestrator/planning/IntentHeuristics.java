package com.lqragent.backend.orchestrator.planning;

/**
 * 轻量意图启发式（不调用 LLM），供 Gate / QA 快通道使用
 */
public final class IntentHeuristics {

    private IntentHeuristics() {}

    /** 出题 / 练习题生成（Phase 3：映射到 quiz_consult pipeline） */
    public static boolean isQuizGenerationIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim();
        if (containsAny(m, "视频", "图片", "示意图", "海报", "动画", "画图", "画一张")) {
            return false;
        }
        if (isObviousQa(m) && !containsAny(m, "出题", "练习题", "道题", "习题", "考题", "刷题", "测验题")) {
            return false;
        }
        return containsAny(m, "出题", "练习题", "习题", "考题", "刷题", "测验题", "考几题")
                || m.matches("(?s).*出\\s*\\d+\\s*道.*题.*")
                || m.matches("(?s).*\\d+\\s*道.*题.*");
    }

    /** 明显问答：可跳过 PlanningAgent 直调 QaAgent */
    public static boolean isObviousQa(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim();
        if (isVagueLearningIntent(m) || isExplicitFullLearningPlan(m)) {
            return false;
        }
        if (containsAny(m, "出题", "练习题", "测验", "视频", "图片", "示意图", "海报", "动画", "学习计划", "学习路径", "路线图", "帮我学", "想学", "入门")) {
            return false;
        }
        return containsAny(m, "什么是", "何为", "是什么", "如何", "怎么", "为什么", "怎样", "解释", "含义", "区别", "原理")
                || m.endsWith("?") || m.endsWith("？");
    }

    /** 模糊学习意图：想学 X 但缺少细节 */
    public static boolean isVagueLearningIntent(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim();
        boolean wantsLearn = containsAny(m, "想学", "帮我学", "学习一下", "入门", "规划", "学习计划", "学习路径", "路线图", "制定计划", "怎么学");
        if (!wantsLearn) {
            return false;
        }
        return !hasSufficientLearningDetails(m);
    }

    /** 用户明确要求完整资源/讲义 */
    public static boolean isExplicitFullLearningPlan(String message) {
        if (message == null) {
            return false;
        }
        String m = message.trim();
        if (containsAny(m, "生成讲义", "完整计划", "完整的学习", "全套", "学习资源", "教案", "课件", "完整路径", "讲义")) {
            return true;
        }
        return m.contains("完整") && containsAny(m, "计划", "讲义", "资源", "路径");
    }

    /** 层 3：用户确认生成讲义/资源 */
    public static boolean isResourceGenerationConfirm(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        if (isResourceGenerationDecline(message)) {
            return false;
        }
        String m = message.trim().toLowerCase();
        if (m.equals("y") || m.equals("yes") || m.equals("是") || m.equals("要")
                || m.equals("好的") || m.equals("好") || m.equals("行")) {
            return true;
        }
        if (containsAny(m, "生成讲义", "生成资源", "要生成", "帮我生成", "开始生成")) {
            return true;
        }
        boolean affirms = containsAny(m, "是的", "好的", "可以", "要", "需要", "确认", "开始", "生成");
        boolean aboutResources = containsAny(m, "讲义", "资源", "练习题", "教案");
        return affirms && (aboutResources || m.length() <= 8);
    }

    /**
     * 等待「是否生成讲义」时，除明确确认/拒绝外的消息视为新意图，应退出确认态。
     */
    public static boolean shouldBreakResourceConfirm(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        return !isResourceGenerationConfirm(message) && !isResourceGenerationDecline(message);
    }

    /** 层 3：用户拒绝生成资源 */
    public static boolean isResourceGenerationDecline(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim().toLowerCase();
        if (m.equals("n") || m.equals("no") || m.equals("否")) {
            return true;
        }
        return containsAny(m, "不用", "不要", "不需要", "跳过", "算了")
                || m.equals("不用了") || m.equals("不要了");
    }

    /** 消息本身已含足够规划信息 */
    public static boolean hasSufficientLearningDetails(String message) {
        if (message == null) {
            return false;
        }
        boolean hasGoal = hasGoalHint(message);
        boolean hasLevel = hasLevelHint(message);
        boolean hasTime = hasTimeHint(message);
        return (hasLevel && hasTime) || (hasGoal && hasLevel) || (hasGoal && hasTime);
    }

    /** 是否已点明学习主题（含 python / java 等） */
    public static boolean hasGoalHint(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim().replace('\n', ' ').toLowerCase();
        return containsAny(m, "数据分析", "web", "django", "找工作", "就业", "项目", "考研", "考试",
                "python", "java", "javascript", "typescript", "c语言", "c++", "go", "rust", "前端", "后端",
                "机器学习", "react", "vue");
    }

    /** 是否已说明基础水平（含 Clarify 常见口语回复） */
    public static boolean hasLevelHint(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim().replace('\n', ' ');
        return containsAny(m, "零基础", "新手", "初学", "有基础", "学过", "入门过", "熟练",
                "一点基础", "有点基础", "有些基础", "会一点", "了解一点", "入门了", "没学过",
                "完全零基础", "项目经验", "有项目", "有一点", "有些");
    }

    /** 是否已说明学习时长 */
    public static boolean hasTimeHint(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim().replace('\n', ' ');
        return m.matches(".*\\d+\\s*(个月|月|周|天|year|years).*")
                || containsAny(m, "三个月", "半年", "一年", "1个月", "2个月", "3个月", "6个月", "4周", "8周")
                || m.contains("个月");
    }

    /** 是否已说明学习目标/程度（入门、求职等） */
    public static boolean hasGoalDegreeHint(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.trim().replace('\n', ' ');
        return containsAny(m, "入门", "求职", "找工作", "就业", "备考", "小项目", "项目实战",
                "了解一下", "先学", "深入", "进阶", "熟练运用");
    }

    private static boolean containsAny(String text, String... needles) {
        String lower = text.toLowerCase();
        for (String n : needles) {
            if (lower.contains(n.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
