# LQRAgent 多智能体系统最终版规划

> **目标：** 构建一个完整的 AI 教育多智能体系统，覆盖学习全生命周期

**当前状态：** 7 个 Agent，完成度 85%

**最终目标：** 20+ 个专业 Agent，覆盖学习科学、内容生成、质量保障、智能服务

---

## 一、系统架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          前端层                                      │
│  聊天学习 │ 学习路径 │ 学习资源 │ 答题练习 │ 学习画像 │ 知识图谱      │
└─────────────────────────────────────────────────────────────────────┘
                              ↓ WebSocket / HTTP
┌─────────────────────────────────────────────────────────────────────┐
│                      OrchestratorCore                                │
│  意图识别 → 任务拆解 → Agent 调度 → 结果聚合                         │
└─────────────────────────────────────────────────────────────────────┘
                              ↓ Redis Streams
┌─────────────────────────────────────────────────────────────────────┐
│                        Agent 层（20+ 个）                            │
├─────────────────────────────────────────────────────────────────────┤
│  学习科学层 │ 内容生成层 │ 质量保障层 │ 智能服务层 │ 用户理解层      │
└─────────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────────┐
│                        基础设施层                                    │
│  LLM API │ MySQL │ Redis │ ai-server │ 知识图谱                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 二、Agent 分层设计

### 2.1 学习科学层（核心创新）

| Agent | 职责 | 优先级 | 状态 |
|-------|------|--------|------|
| **KnowledgeStateAgent** | 追踪每个知识点的掌握度 | P0 | 🔴 待开发 |
| **SpacedRepetitionAgent** | 间隔复习调度（遗忘曲线） | P0 | 🔴 待开发 |
| **DifficultyAgent** | 自适应难度调整 | P1 | 🔴 待开发 |
| **LearningStyleAgent** | 学习风格识别（视觉/听觉/动手） | P2 | 🔴 待开发 |

### 2.2 内容生成层

| Agent | 职责 | 优先级 | 状态 |
|-------|------|--------|------|
| **LessonAgent** | 讲义生成 | P0 | ✅ 已有（ResourceAgent） |
| **QuizAgent** | 练习题生成 | P0 | ✅ 已有（ResourceAgent） |
| **CodeAgent** | 代码示例生成 | P0 | ✅ 已有（ResourceAgent） |
| **DiagramAgent** | 图表/思维导图生成 | P1 | 🔴 待开发 |
| **VideoAgent** | 视频脚本/动画生成 | P2 | 🔴 待开发 |
| **SummaryAgent** | 总结/复习材料生成 | P1 | 🔴 待开发 |

### 2.3 质量保障层

| Agent | 职责 | 优先级 | 状态 |
|-------|------|--------|------|
| **ContentQualityAgent** | 内容质量检查 | P0 | ⚠️ 已有（待完善） |
| **PedagogyQualityAgent** | 教学质量检查 | P1 | 🔴 待开发 |
| **FactCheckAgent** | 事实准确性检查 | P1 | 🔴 待开发 |

### 2.4 智能服务层

| Agent | 职责 | 优先级 | 状态 |
|-------|------|--------|------|
| **QaAgent** | 答疑解惑 | P0 | ✅ 已有 |
| **RecommendationAgent** | 个性化推荐 | P0 | 🔴 待开发 |
| **InterventionAgent** | 学习干预（发现问题主动调整） | P1 | 🔴 待开发 |
| **MotivationAgent** | 激励/游戏化 | P2 | 🔴 待开发 |
| **AssessmentAgent** | 评估/批改 | P1 | 🔴 待开发 |

### 2.5 用户理解层

| Agent | 职责 | 优先级 | 状态 |
|-------|------|--------|------|
| **ProfileAgent** | 学习画像 | P0 | ✅ 已有 |
| **BehaviorAgent** | 行为分析 | P1 | 🔴 待开发 |
| **PreferenceAgent** | 偏好学习 | P2 | 🔴 待开发 |

### 2.6 路径规划层

| Agent | 职责 | 优先级 | 状态 |
|-------|------|--------|------|
| **LearningPathAgent** | 学习路径规划 | P0 | ✅ 已有 |
| **KnowledgeGraphAgent** | 知识图谱管理 | P1 | 🔴 待开发 |
| **PrerequisiteAgent** | 前置知识检查 | P1 | 🔴 待开发 |

---

## 三、实施计划

### Phase 1：基础完善（1-2 周）

**目标：** 让现有 7 个 Agent 都能真正工作

| 任务 | 说明 | 工作量 |
|------|------|--------|
| 1.1 完善 QualityAgent 工具 | 实现 CheckQualityTool 的 TODO | 1 天 |
| 1.2 完善 EffectAgent 工具 | 实现 AnalyzeWeaknessTool 的 TODO | 1 天 |
| 1.3 完善 QaAgent 工具 | SearchKnowledgeTool 接入 RAG | 1 天 |
| 1.4 拆分 ResourceAgent | 拆成 LessonAgent、QuizAgent、CodeAgent | 2 天 |
| 1.5 Agent 监控面板 | 实时查看 Agent 状态 | 1 天 |

### Phase 2：学习科学（2-3 周）

**目标：** 实现核心学习科学功能

| 任务 | 说明 | 工作量 |
|------|------|--------|
| 2.1 KnowledgeStateAgent | 知识状态追踪（掌握度、遗忘曲线） | 3 天 |
| 2.2 SpacedRepetitionAgent | 间隔复习调度 | 2 天 |
| 2.3 DifficultyAgent | 自适应难度调整 | 2 天 |
| 2.4 InterventionAgent | 学习干预（主动发现问题） | 2 天 |
| 2.5 知识状态数据库 | 设计知识状态表结构 | 1 天 |

### Phase 3：内容增强（2-3 周）

**目标：** 丰富内容生成能力

| 任务 | 说明 | 工作量 |
|------|------|--------|
| 3.1 DiagramAgent | 图表/思维导图生成 | 3 天 |
| 3.2 SummaryAgent | 总结/复习材料生成 | 2 天 |
| 3.3 RecommendationAgent | 个性化推荐 | 3 天 |
| 3.4 AssessmentAgent | 评估/批改 | 2 天 |
| 3.5 多模态支持 | 图片生成、图表渲染 | 2 天 |

### Phase 4：质量保障（1-2 周）

**目标：** 提升内容质量

| 任务 | 说明 | 工作量 |
|------|------|--------|
| 4.1 PedagogyQualityAgent | 教学质量检查 | 2 天 |
| 4.2 FactCheckAgent | 事实准确性检查 | 2 天 |
| 4.3 质量反馈循环 | 质量问题自动修复 | 2 天 |

### Phase 5：高级功能（3-4 周）

**目标：** 实现高级智能功能

| 任务 | 说明 | 工作量 |
|------|------|--------|
| 5.1 LearningStyleAgent | 学习风格识别 | 2 天 |
| 5.2 BehaviorAgent | 行为分析 | 2 天 |
| 5.3 MotivationAgent | 激励/游戏化 | 3 天 |
| 5.4 VideoAgent | 视频脚本生成 | 3 天 |
| 5.5 KnowledgeGraphAgent | 知识图谱管理 | 3 天 |
| 5.6 PrerequisiteAgent | 前置知识检查 | 2 天 |

---

## 四、数据库设计

### 4.1 新增表

```sql
-- 知识状态表
CREATE TABLE knowledge_state (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    kp_id VARCHAR(50) NOT NULL,
    mastery_level DECIMAL(3,2) DEFAULT 0.00,  -- 掌握度 0-1
    last_review_at DATETIME,
    next_review_at DATETIME,
    review_count INT DEFAULT 0,
    correct_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_kp (user_id, kp_id)
);

-- 学习行为表
CREATE TABLE learning_behavior (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,  -- view, quiz, review, etc.
    kp_id VARCHAR(50),
    resource_id BIGINT,
    duration_seconds INT,
    score DECIMAL(5,2),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, created_at)
);

-- 复习计划表
CREATE TABLE review_schedule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    kp_id VARCHAR(50) NOT NULL,
    scheduled_at DATETIME NOT NULL,
    completed_at DATETIME,
    interval_days INT DEFAULT 1,
    ease_factor DECIMAL(3,2) DEFAULT 2.50,
    status VARCHAR(20) DEFAULT 'pending',
    INDEX idx_user_schedule (user_id, scheduled_at)
);
```

---

## 五、Agent 接口规范

### 5.1 Agent 基类

```java
public abstract class BaseAgent {
    // 必须实现
    protected abstract String getSystemPrompt();
    protected abstract List<AgentTool> getTools();
    protected abstract String buildUserMessage(AgentRequest request);
    
    // 可选覆盖
    protected int getMaxIterations() { return 5; }
    protected double getTemperature() { return 0.1; }
    protected String getModel() { return null; }  // 使用默认模型
}
```

### 5.2 工具接口

```java
public interface AgentTool {
    String name();
    String description();
    Map<String, Object> parameterSchema();
    ToolResult execute(Map<String, Object> args);
}
```

### 5.3 消息格式

```json
{
    "taskId": "uuid",
    "sender": "agent_id",
    "receiver": "agent_id",
    "performative": "REQUEST|INFORM|PROGRESS|ERROR",
    "content": {
        "action": "tool_name",
        "params": {}
    },
    "timestamp": 1234567890
}
```

---

## 六、验证标准

### 6.1 单 Agent 验证

- [ ] Agent 能正确加载 system prompt
- [ ] Agent 能调用 LLM 进行推理
- [ ] Agent 能执行工具并返回结果
- [ ] Agent 能处理错误情况

### 6.2 系统集成验证

- [ ] OrchestratorCore 能正确识别意图
- [ ] 能通过 Redis Streams 调度 Agent
- [ ] 能聚合多个 Agent 的结果
- [ ] 前端能正确显示 Agent 状态

### 6.3 端到端验证

- [ ] 聊天学习：用户提问 → QA Agent 回答
- [ ] 学习路径：输入目标 → 生成路径 → 动态生成
- [ ] 资源生成：选择知识点 → 生成讲义/练习题
- [ ] 质量检查：生成内容 → 质量评估 → 反馈

---

## 七、技术债务

| 问题 | 影响 | 解决方案 |
|------|------|----------|
| ResourceAgent 职责过重 | 难以维护 | 拆分成 3 个专注 Agent |
| 缺少 Agent 记忆 | 每次对话独立 | 实现记忆系统 |
| 缺少错误恢复 | 工具失败无法重试 | 实现重试机制 |
| 缺少监控 | 无法追踪问题 | 实现监控面板 |

---

## 八、里程碑

| 里程碑 | 目标 | 预计时间 |
|--------|------|----------|
| M1 | 所有 Agent 能真正工作 | 2 周 |
| M2 | 实现学习科学核心功能 | 4 周 |
| M3 | 丰富内容生成能力 | 6 周 |
| M4 | 完善质量保障 | 8 周 |
| M5 | 实现高级功能 | 12 周 |

---

*文档版本：v1.0*
*最后更新：2026-06-04*
