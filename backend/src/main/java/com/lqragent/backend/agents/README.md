# 智能体模块目录说明

本项目采用多 Agent 协作架构，通过 Orchestrator 总调度 + 专业 Agent 组件实现个性化学习功能。

## 目录结构

```
agents/
├── orchestrator/          # 总调度（Function Calling 意图识别）
├── base/                  # 基础类和接口
├── user/                  # 用户层 Agent
│   ├── profile/           # 用户画像分析
│   └── behavior/          # 用户行为分析（待实现）
├── learn/                 # 学习层 Agent
│   ├── learningstyle/     # 学习风格检测
│   ├── knowledgestate/    # 知识掌握度追踪
│   ├── spacedrepetition/  # 间隔复习调度
│   ├── difficulty/        # 自适应难度调整
│   ├── state/             # 学习状态追踪
│   ├── path/              # 学习路径规划
│   └── adapt/             # 自适应学习（待实现）
├── serve/                 # 服务层 Agent
│   ├── qa/                # 智能问答（ReAct 模式）
│   ├── recommendation/    # 学习推荐
│   ├── assessment/        # 评估批改
│   └── intervention/      # 学习干预
├── content/               # 内容层 Agent
├── check/                 # 检查层 Agent
├── shared/                # 跨 Agent 共享
│   └── llm/               # LLM 客户端（支持 Function Calling）
└── orch/                  # 编排层（空目录，规划中）
```

## 已实现 Agent 组件

| 分类 | Agent | 说明 |
|------|-------|------|
| **调度层** | Orchestrator | Function Calling 意图识别 + Pipeline 调度 |
| | PlanningAgent | LLM 任务拆解 |
| **用户层** | ProfileAgent | 用户画像分析 |
| | LearningStyleAgent | 学习风格检测 |
| **学习层** | KnowledgeStateAgent | 知识掌握度追踪 |
| | SpacedRepetitionAgent | 间隔复习调度 |
| | DifficultyAgent | 自适应难度调整 |
| | EffectAgent | 学习效果评估 |
| **内容层** | QaAgent | ReAct 问答（含 RAG） |
| | LessonAgent | 讲义生成 |
| | DiagramAgent | 图表生成 |
| | SummaryAgent | 总结生成 |
| **服务层** | RecommendationAgent | 学习推荐 |
| | AssessmentAgent | 评估批改 |
| | InterventionAgent | 学习干预 |
| **基础层** | LearningPathService | 学习路径规划 |

## 核心架构

### 意图识别（Function Calling）

项目使用 LLM Function Calling 进行意图识别，定义了 6 个工具：
- `route_greeting` - 纯粹问候
- `route_help` - 询问功能/帮助
- `route_learning_path` - 学习路径规划
- `route_resource_generate` - 资源生成
- `route_qa` - 智能问答
- `route_diagram` - 图表生成

### QA Agent（ReAct 模式）

QaAgent 采用 ReAct（Reasoning + Acting）模式：
1. **思考** - LLM 分析问题并决定下一步行动
2. **行动** - 调用工具（RAG 搜索、代码执行等）
3. **观察** - 获取工具执行结果
4. **重复** - 直到得出最终答案

### Agent ID 常量

统一在 `com.lqragent.backend.agent.AgentIds` 中定义，禁止硬编码字符串。

## 技术栈

- **LLM 客户端**：LlmClient（支持 Function Calling）
- **向量数据库**：EmbeddingStore
- **知识库**：KnowledgeBaseService
- **会话管理**：ChatSessionService

## 框架层

`com.lqragent.backend.agent` 包保留 Agent 运行时框架（AgentBus、AgentEngine、ToolRegistry 等），不含具体业务智能体。
