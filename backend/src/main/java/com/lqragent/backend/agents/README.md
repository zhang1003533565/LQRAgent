# 智能体模块目录说明

每个子目录对应一个独立智能体，内含该 Agent 的入口类、业务 Service、Controller、实体与 DTO。

## 目录结构

```
agents/
├── orchestrator/          # 总调度
├── learner_profile/       # 学生画像
├── learning_path/         # 学习路径
├── resource_generation/   # 个性化资源生成
├── quality_assessment/    # 质量评估
├── effect_assessment/     # 效果评估
├── intelligent_qa/        # 智能答疑
├── content_analyzer/      # 内容分析
├── media_generation/      # 媒体/示意图生成
└── shared/                # 跨 Agent 共享
    ├── knowledgegraph/    # 知识图谱
    └── llm/               # LlmContentGenerator
```

## Agent ID 常量

统一在 `com.lqragent.backend.agent.AgentIds` 中定义，禁止硬编码字符串。

| 常量 | 值 | 说明 |
|------|-----|------|
| ORCHESTRATOR | orchestrator | 总调度 |
| LEARNER_PROFILE | learner_profile | 学生画像 |
| LEARNING_PATH | learning_path | 学习路径 |
| RESOURCE_GENERATION | resource_generation | 资源生成 |
| QUALITY_ASSESSMENT | quality_assessment | 质量评估 |
| EFFECT_ASSESSMENT | effect_assessment | 效果评估 |
| INTELLIGENT_QA | intelligent_qa | 智能答疑 |
| CONTENT_ANALYZER | content_analyzer | 内容分析 |
| MEDIA_GENERATION | media_generation | 媒体生成 |

## 框架层

`com.lqragent.backend.agent` 包保留 Agent 运行时框架（AgentBus、AgentEngine、ToolRegistry 等），不含具体业务智能体。
