package com.lqragent.backend.core.agent;

/**
 * 全系统智能体标识符（与 WS agent_step、AgentBus 路由一致）。
 * 使用 snake_case，语义清晰，便于日志与前端展示。
 */
public final class AgentIds {

    private AgentIds() {}

    /** 总调度：意图识别、子 Agent 派发 */
    public static final String ORCHESTRATOR = "orchestrator";

    /** 学生画像：6 维特征增量抽取 */
    public static final String LEARNER_PROFILE = "learner_profile";

    /** 学习路径：DAG 拓扑 + 画像融合 */
    public static final String LEARNING_PATH = "learning_path";

    /** 个性化资源：讲义 / 习题 / 代码 / 思维导图 / 拓展阅读 */
    public static final String RESOURCE_GENERATION = "resource_generation";

    /** 质量评估：四层安全管道 */
    public static final String QUALITY_ASSESSMENT = "quality_assessment";

    /** 效果评估：答题与行为分析、路径变轨 */
    public static final String EFFECT_ASSESSMENT = "effect_assessment";

    /** 智能答疑：流式问答 */
    public static final String INTELLIGENT_QA = "intelligent_qa";

    /** 内容分析：上传资料解析 */
    public static final String CONTENT_ANALYZER = "content_analyzer";

    /** 媒体生成：示意图 / 流程图 */
    public static final String MEDIA_GENERATION = "media_generation";
}
