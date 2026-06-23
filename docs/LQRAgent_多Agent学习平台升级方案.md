# LQRAgent 多 Agent 学习平台升级方案

> **版本**：v1.2  
> **日期**：2026-06-23  
> **状态**：Sprint 1–3 已落地；Sprint 4 待做（Pipeline 重试 + 学习闭环对齐）  
> **适用范围**：后端优先改造 + 控制台/管理后台可测 + 前端对接清单

---

## 1. 文档目的

本文档在代码复盘基础上，明确 LQRAgent 从「多 Agent 聊天 Demo」升级为「多 Agent 学习平台」的：

- 架构边界与主路径选择
- 现状问题与风险（不含密钥硬编码类安全问题）
- 分阶段后端改造计划
- 控制台 / REST 测试体系
- 前端后续对接清单
- 验收标准

**复盘结论：方向正确，可实施。** 核心策略为：**Java Backend 做平台大脑与业务编排，ai-server（DeepTutor）做 AI 能力 SDK，React 前端做学习产品 UI。**

---

## 2. 项目定位与实现思路

### 2.1 原始设计意图

| 层级 | 职责 |
|------|------|
| **ai-server** | 成熟开源项目（DeepTutor）：LLM、RAG、知识库、capability |
| **backend** | Spring Boot：能调 ai-server 的直接调；没有的或不合适的自研 |
| **frontend** | 自研 React 学习工作台（不使用 DeepTutor 自带 Next.js UI） |
| **MySQL** | 用户、路径、Quiz、记忆、Pipeline 任务等业务数据 |

### 2.2 当前集成现状（已验证）

| 集成方式 | 类 | ai-server 能力 |
|----------|-----|----------------|
| REST | `AiServerClient` | 知识库 CRUD、health、上传、reindex |
| WS 旧聊天 | `AiServerWsProxy.streamChat` | `/api/v1/chat` + RAG |
| WS capability | `AiServerWsProxy.callCapability` | `deep_solve` / `deep_question` / `visualize` |
| WS Agentic Pipeline | `AiServerWsProxy.startSession` | `/api/v1/ws`（可选路径） |
| Agent 工具 | `AiServerCapabilityTool` + `AiServerToolFactory` | capability 封装为 Java Agent 工具 |
| RAG | `RagSearchTool` | 知识库检索 |

### 2.3 必须在 Backend 自研的部分

| 能力 | 原因 |
|------|------|
| PlanningAgent / TaskPlan | 需理解学习路径、Quiz、媒体等业务意图 |
| PipelineEngine + QualityGate | 需接 MySQL、WS 进度、任务持久化 |
| 学习路径 / Quiz / 画像 / UserMemory | 赛题与平台业务数据 |
| learning_loop Pipeline | 答题 → 批改 → 薄弱点 → 调路径 → 推资源 |
| Artifact 协议 + WS 推送 | 统一前端渲染 |
| PipelineTask 持久化 / 重试 | 平台 SLA |

---

## 3. 架构决策（复盘确认）

### 3.1 主路径：Java Orchestrator，非 ai-server Agentic Pipeline

`ChatWebSocketHandler` 存在两条路径：

```
ai-server.use-agentic-pipeline = true  → AiServerWsProxy.startSession（DeepTutor 内部编排）
ai-server.use-agentic-pipeline = false → OrchestratorCore → PlanningAgent → PipelineEngine（默认）
```

**决策**：

- **平台主路径**：`false`（当前 `application.properties` 默认值已是 `false`）
- **ai-server Agentic Pipeline**：仅作对比实验或降级，不作为产品主链路
- **理由**：学习路径、Quiz 闭环、PipelineTask、Artifact WS 事件均围绕 Java 路径设计；DeepTutor 不了解平台业务表结构

### 3.2 能力分层

```
┌─────────────────────────────────────────────────────────┐
│ Frontend（React）— 学习工作台、聊天、路径、Quiz、图谱      │
└───────────────────────────┬─────────────────────────────┘
                            │ REST / WebSocket
┌───────────────────────────▼─────────────────────────────┐
│ Backend 平台层                                           │
│  OrchestratorCore → PlanningAgent → PipelineEngine       │
│  20 Java Agents + QualityGate + PipelineTask + 业务 Service│
└───────────────────────────┬─────────────────────────────┘
                            │ AiServerClient / AiServerWsProxy / AgentTool
┌───────────────────────────▼─────────────────────────────┐
│ ai-server（DeepTutor）— LLM / RAG / capability / 知识库   │
└─────────────────────────────────────────────────────────┘
                            │
                       MySQL + Redis
```

### 3.3 不做的事（避免过度工程）

| 不做 | 原因 |
|------|------|
| 全面 A2A 动态协作 | `requestPeer` 已实现但零调用；静态 Pipeline + 少量 peer 试点足够 |
| 新增更多 Agent | 现有 20 个已覆盖核心场景，先补齐 Card / Artifact / 测试 |
| 重写 ai-server | 只通过 Tool 封装调用 |
| 嵌入 DeepTutor Web UI | 前端继续自研 React |
| 微服务拆分 | 单体 + Redis Streams + PipelineTask 表足够 |

---

## 4. 现状复盘（2026-06-23 代码基线）

### 4.1 已完成（Sprint 1–3）

| 模块 | 状态 | 说明 |
|------|------|------|
| PlanningAgent v4 | ✅ | TaskPlan + create_plan / route_simple / ask_clarify |
| 测试观测层 | ✅ | OrchestratorTestService + `/api/test/*` + 控制台 plan/pipeline/agents |
| AgentCardRegistry | ✅ | **20/20** Agent 已实现 `getAgentCard()` |
| Artifact 协议统一 | ✅ | `ArtifactExtractor` + `StepStreamPolicy`；`ChatRouteDispatcher` 无 `agentId.contains` |
| QualityGate | ✅ | 已接入 PipelineEngine |
| PipelineTask 持久化 | ✅ | DB + REST 查询 API |
| learning_loop | ✅ | QuizService 异步触发 |
| ai-server 工具 | ✅ | QaAgent/QuizAgent/DiagramAgent + `/api/test/aiserver-tools` |
| multimodal Pipeline | ✅ | ensureMediaSteps 兜底媒体规划 |
| 能力发现单轨 | ✅ | HELP/简单路由/CFP 均走 `AgentCardRegistry`；已删除 `CapabilityRegistry` |

### 4.2 未完成 / 存在问题（Sprint 4 及后续）

| 问题 | 影响 | 优先级 |
|------|------|--------|
| 图片 metadata 重进会话 | 部分历史消息仍显示 prompt 文本 | P1 |
| `/api/test/quiz-submit` 默认 legacy 模式 | 测试与线上 learning_loop 需手动 `mode=loop` | P1 |
| 前端 `pipelineTaskApi` 零引用 | 刷新丢失 Pipeline 进度 | P1（前端） |
| Pipeline retry API 未实现 | 失败后只能全量重跑 | P1 |
| 意图回归未纳入 CI | 规划稳定性难持续验证 | P1 |
| `requestPeer` 无调用 | A2A 名存实亡（可接受） | P2 |
| ai-server 直调 Proxy 未完全收口 | 部分 Service 仍可能 bypass Tool | P2 |

### 4.3 AgentCard 覆盖明细

**20/20 均已实现 `getAgentCard()`**（Sprint 3 补全 7 个）：

QaAgent、QuizAgent、LearningPathAgent、ProfileAgent、ContentAnalysisAgent、PromptGenAgent、MediaGenAgent、QualityAgent、AssessmentAgent、EffectAgent、RecommendationAgent、SummaryAgent、DiagramAgent、**LessonAgent、ResourceAgent、DifficultyAgent、KnowledgeStateAgent、LearningStyleAgent、SpacedRepetitionAgent、InterventionAgent**

### 4.4 现有测试入口

| 入口 | 路径 | 说明 |
|------|------|------|
| 仅 plan | `POST /api/test/plan` | 意图规划，不执行 Pipeline |
| 同步 Pipeline | `POST /api/test/pipeline` | 含 stepResults、artifacts |
| AgentCard 目录 | `GET /api/test/agent-cards` | 应返回 count≥20 |
| 意图回归 | `POST /api/test/intent-suite` | 批量意图用例 |
| learning_loop | `POST /api/test/learning-loop` | 同步闭环测试 |
| 控制台 | `plan` / `pipeline` / `agents` / `intent` | 与 REST 共用 OrchestratorTestService |
| WebSocket | 聊天 | 完整链路 E2E |

---

## 5. 升级目标

### 5.1 平台级目标

1. **可编排**：用户一句话 → PlanningAgent 产出 TaskPlan → Pipeline 多 Agent 协作
2. **可观测**：控制台 / REST / 管理后台能看到 plan、逐步结果、artifacts、质检报告
3. **可闭环**：Quiz 提交 → learning_loop → 路径/薄弱点/资源联动
4. **可恢复**：Pipeline 任务持久化，支持失败重试、刷新恢复（前端后续接）
5. **边界清晰**：ai-server 只管 AI 能力，backend 管学习业务

### 5.2 非目标（本阶段）

- 不做 ai-server fork 定制
- 不做全面 Agent 间动态 DAG
- 不做前端大改版（仅列清单）

---

## 6. 分阶段实施计划

### Sprint 1：测试与观测层（1–3 天）【最先做】

**目标**：不改业务逻辑，让 Planning → Pipeline → Step 在 HTTP/控制台可见。

#### 6.1.1 新建 OrchestratorTestService

**路径**：`backend/src/main/java/com/lqragent/backend/orchestrator/service/OrchestratorTestService.java`

```java
PlanTestResult planOnly(userId, message, chatHistory);
PipelineTestResult runPipelineSync(userId, message);
List<AgentCardDto> listAgentCards();
CapabilityTestResult testCapability(name, args);
LearningLoopTestResult runLearningLoop(userId, questionId, answers);
IntentSuiteResult runIntentSuite();
```

统一返回结构：

```json
{
  "planType": "PLAN|PIPELINE|SIMPLE|CLARIFY",
  "pipelineId": "dynamic_plan-1730000000",
  "goal": "用户原始输入",
  "steps": [
    {"stepId": "s1", "agentId": "qa_agent", "action": "process", "dependsOn": []}
  ],
  "stepResults": [
    {
      "stepId": "s1",
      "agentId": "qa_agent",
      "success": true,
      "durationMs": 3200,
      "summary": "...",
      "artifacts": [
        {"kind": "text", "producerAgentId": "qa_agent", "confidence": 0.8, "payload": {}}
      ],
      "qualityReport": {"passed": true, "issues": []}
    }
  ],
  "artifacts": [],
  "durationMs": 15000,
  "error": null
}
```

#### 6.1.2 扩展 REST 测试 API

**文件**：`AgentTestController.java`（或新建 `OrchestratorTestController`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/test/plan` | 仅 planOnly |
| POST | `/api/test/pipeline` | 同步完整 Pipeline |
| GET | `/api/test/agent-cards` | AgentCardRegistry 列表 |
| POST | `/api/test/capability/{name}` | 测 ai-server capability |
| POST | `/api/test/learning-loop` | 跑 learning_loop Pipeline |
| POST | `/api/test/intent-suite` | 批量意图回归 |

**改造现有接口**：

- `/api/test/agent`：增加 planType、steps、stepResults
- `/api/test/quiz-submit`：增加 `mode=legacy|loop`
- `/admin/agent-test`：返回字段与上述对齐

#### 6.1.3 扩展 ConsoleControlPanel 命令

| 命令 | 功能 |
|------|------|
| `plan <消息>` | 打印 TaskPlan 步骤表 |
| `pipeline <消息>` | 同步跑 Pipeline，逐步输出 ✓/✗ |
| `agents` | 打印 AgentCard 列表 |
| `capability <名> <json>` | 测 ai-server 工具 |
| `loop <questionId> <score>` | 触发 learning_loop |
| `pipeline-status [taskId]` | 查 PipelineTask |
| `intent` | 跑意图测试集，输出通过率 |
| `status`（扩展） | AgentCard 数量、最近任务、ai-server 状态 |

控制台与 REST **共用 OrchestratorTestService**，避免双份逻辑。

#### Sprint 1 验收 ✅（2026-06-23 已通过）

```powershell
# 控制台
plan 帮我学 Python 装饰器
pipeline 出 5 道闭包练习题
agents
ai ping

# curl
curl -X POST http://localhost:8080/api/test/plan -H "Content-Type: application/json" -d "{\"message\":\"用视频解释 Agent\"}"
curl http://localhost:8080/api/test/agent-cards
```

---

### Sprint 2：Artifact 协议统一（3–5 天）✅ 已落地

**目标**：Pipeline 产物走 `List<Artifact>`，WS 按 `kind` 分发。

#### 6.2.1 BaseAgent 统一 convertToResponse

**文件**：`orchestrator/agents/BaseAgent.java`

从 `AgentMessage` / `metadata.artifacts` / 旧 `artifactKind+artifactPayload` 统一写入 `AgentResponse.artifacts`。

#### 6.2.2 核心 Agent 改 successWithArtifact

| Agent | ArtifactKind |
|-------|--------------|
| QaAgent | TEXT, RAG_SOURCES |
| QuizAgent | QUIZ |
| LearningPathAgent | LEARNING_PATH |
| MediaGenAgent | IMAGE / VIDEO |
| DiagramAgent | DIAGRAM |

#### 6.2.3 重构 ChatRouteDispatcher

**文件**：`chat/handler/ChatRouteDispatcher.java`

- 删除 `agentId.contains("learning_path")` 等硬编码分支
- 主路径：遍历 `AgentResponse.getArtifacts()` 推 WS
- 保留 `sendLegacyArtifacts()` 兼容旧 Agent

#### 6.2.4 OrchestratorCore 同步返回 artifacts

`handlePipelineRequest` 与 `/api/test/pipeline` 聚合所有 step 的 artifacts。

#### Sprint 2 验收 ✅（2026-06-23 已通过）

```
pipeline 出 3 道 Python 装饰器题     → 含 QUIZ artifact，questions 非空
pipeline 画一张装饰器示意图         → 含 IMAGE artifact，url 有效
```

---

### Sprint 3：AgentCard 补全 + 删除双轨（2–3 天）✅ 已落地

#### 6.3.1 7 个 Agent 补 getAgentCard

| Agent | outputArtifactKinds |
|-------|---------------------|
| LessonAgent | lesson, text |
| ResourceAgent | lesson, quiz, multi_card |
| DifficultyAgent | profile |
| KnowledgeStateAgent | weakness_profile, profile |
| LearningStyleAgent | profile |
| SpacedRepetitionAgent | text |
| InterventionAgent | text, learning_path |

#### 6.3.2 删除 CapabilityRegistry 业务依赖

| 操作 | 文件 |
|------|------|
| 删除 `registerAgentCapabilities()` | OrchestratorCore.java |
| HELP 改读 `AgentCardRegistry.buildCatalog()` | OrchestratorCore.java |
| 移除注入 | BaseAgent.java, OrchestratorCore.java |
| 删除类 | ~~capability/CapabilityRegistry.java~~ ✅ 已删 |
| 删除类 | ~~capability/AgentCapability.java~~ ✅ 已删 |
| 更新测试 | ~~CapabilityRegistryTest.java~~ → `AgentCardRegistryTest.java` ✅ |

#### 6.3.3 ai-server 调用收口

所有 ai-server 调用必须经 `AgentTool`（RagSearchTool / AiServerCapabilityTool），禁止 Service 直调 Proxy。

新增 `GET /api/test/aiserver-tools` 列出已封装工具。

#### Sprint 3 验收 ✅（2026-06-23 已通过）

```
agents          → count=20，无 "Agent xxx_agent" 默认描述
plan 帮我学 Python → 应含 learning_path 相关步骤，非纯 QA
聊天「你能做什么」  → 约 20 条中文能力说明
```

---

### Sprint 4：Pipeline 可靠 + 学习闭环（3–5 天）

#### 6.4.1 Pipeline 失败重试

| 改动 | 文件 |
|------|------|
| `pipeline_task.failed_step` 字段 | init-tables.sql, PipelineTask.java |
| `PipelineEngine.executeFromStep()` | PipelineEngine.java |
| `POST /api/pipeline/tasks/{taskId}/retry` | PipelineTaskController.java |
| 控制台 `retry <taskId>` | ConsoleControlPanel.java |

#### 6.4.2 learning_loop 测试对齐

`/api/test/learning-loop` 与 `QuizService` 线上逻辑一致，返回 4 步：

```
assessment → effect → path_adjust → resource_push
```

#### 6.4.3 意图回归测试集

**文件**：`PlanningAgentIntentTest.java` + `/api/test/intent-suite`

| 输入 | 期望 |
|------|------|
| 你好 | SIMPLE / GREETING |
| 帮我学 Python | PLAN/PIPELINE，含 learning_path |
| 出 5 道题 | PLAN，含 quiz_agent |
| 用视频解释闭包 | PLAN，含 media_gen |
| 什么是装饰器 | PLAN 单步 qa 或 SIMPLE/QA |

#### 6.4.4 LearnerContextService（可选）

Pipeline 启动前注入 profile + mastery + memory 摘要；PlanningAgent 读 UserMemory。

测试：`GET /api/test/learner-context?userId=1`

---

## 7. ai-server 边界约定

### 7.1 继续调用 ai-server

| 能力 | 方式 |
|------|------|
| RAG / 向量检索 | AiServerClient + RagSearchTool |
| 文档上传向量化 | upload 队列 → AiServerClient |
| deep_solve / deep_question / visualize | AiServerCapabilityTool |
| LLM 直连 | callLlmDirect（Agent 内部 fallback） |
| 健康检查 | /api/v1/knowledge/health |

### 7.2 不在 ai-server 实现

- 学习路径 CRUD 与业务规则
- Quiz 记录与 learning_loop 编排
- 用户画像 / 掌握度 / UserMemory
- PipelineTask / 进度推送 / 重试
- Artifact WS 协议

### 7.3 内部约定文档（建议新建）

`docs/ai-server-boundary.md`：团队共识，防止后续在 Java 重写 RAG 或在 ai-server fork 业务逻辑。

---

## 8. 测试体系

### 8.1 三层测试

| 层级 | 方式 | 用途 |
|------|------|------|
| L1 控制台 | ConsoleControlPanel 命令 | 开发时快速验证 |
| L2 REST | `/api/test/*` + Swagger | 自动化 / 管理后台 Network |
| L3 单元测试 | JUnit intent-suite | CI 回归 |

### 8.2 日常回归矩阵

| 场景 | 控制台 | REST |
|------|--------|------|
| ai-server 连通 | `ai ping` | GET `/admin/ai-status` |
| Agent 目录 | `agents` | GET `/api/test/agent-cards` |
| 意图规划 | `plan <msg>` | POST `/api/test/plan` |
| 完整 Pipeline | `pipeline <msg>` | POST `/api/test/pipeline` |
| ai-server 工具 | `capability deep_solve {...}` | POST `/api/test/capability/deep_solve` |
| 学习闭环 | `loop <qid> <score>` | POST `/api/test/learning-loop` |
| 意图回归 | `intent` | POST `/api/test/intent-suite` |
| 任务重试 | `retry <taskId>` | POST `/api/pipeline/tasks/{id}/retry` |

### 8.3 端到端验收（T1–T8）

| # | 输入 | 期望 |
|---|------|------|
| T1 | 你好 | 文字问候 |
| T2 | 你能做什么 | 帮助说明（AgentCard 目录） |
| T3 | 什么是闭包 | 文字讲解 + RAG 来源 |
| T4 | 用视频解释 Agent | 多步 Pipeline + 真 video URL |
| T5 | 画装饰器示意图 | 多步 Pipeline + 图片 URL |
| T6 | 出 5 道闭包题 | QUIZ artifact |
| T7 | 提交 quiz 答案 | learning_loop 四步完成 |
| T8 | 生成 Python 学习路线 | LEARNING_PATH artifact |

---

## 9. 前端对接清单（本阶段不改代码，留接口）

后端 Sprint 1–2 完成后，前端按以下顺序接入：

| 优先级 | 改动 | 依赖 |
|--------|------|------|
| P0 | `artifact.ts` 补 text/assessment/weakness_profile/profile/summary | Sprint 2 |
| P0 | `wsMessageDispatcher` 处理 task_plan、quality_report；pipeline_error 显示错误 | Sprint 1 WS 推送 |
| P0 | `AppLayout.tsx` userId 改 authStore | — |
| P1 | 新建 TaskPlanViewer.tsx | plan 结构 |
| P1 | ChatView 挂载时 pipelineTaskApi.getLatest() | PipelineTask API（已有） |
| P1 | AssessmentCard、WeaknessCard | learning_loop artifacts |
| P2 | Artifact 渲染器注册表 | kinds 稳定 |
| P2 | 管理后台展示 plan steps + artifacts | agent-test  enriched |
| P2 | Pipeline 失败重试按钮 | Sprint 4 retry API |
| P2 | vite.config.ts manualChunks | 性能 |

---

## 10. 文件改动总表

### 新建

| 文件 | Sprint |
|------|--------|
| `orchestrator/service/OrchestratorTestService.java` | 1 |
| `orchestrator/dto/*TestResult.java` | 1 |
| `orchestrator/context/LearnerContextService.java` | 4 |
| `test/.../PlanningAgentIntentTest.java` | 4 |
| `docs/ai-server-boundary.md` | 1 |

### 改造

| 文件 | Sprint | 要点 |
|------|--------|------|
| AgentTestController.java | 1 | 新测试端点 |
| ConsoleControlPanel.java | 1,4 | 新命令 |
| AdminController.java | 1 | agent-test  enriched |
| BaseAgent.java | 2 | artifacts 提取 |
| ChatRouteDispatcher.java | 2 | 按 kind 分发 |
| 5 个核心 Agent | 2 | successWithArtifact |
| 7 个 Agent | 3 | getAgentCard |
| OrchestratorCore.java | 3 | 删 CapabilityRegistry |
| PipelineEngine.java | 4 | executeFromStep |
| PipelineTaskController.java | 4 | retry |
| QuizService.java | 4 | 测试入口复用 |

### 删除

| 文件 | Sprint | 状态 |
|------|--------|------|
| capability/CapabilityRegistry.java | 3 | ✅ 已删 |
| capability/AgentCapability.java | 3 | ✅ 已删 |
| test/.../CapabilityRegistryTest.java | 3 | ✅ 已删 |
| AgentResponse 的 @Deprecated 四字段 | 后续 | 待清理 |

---

## 11. 风险与应对

| 风险 | 应对 |
|------|------|
| LLM 规划不稳定 | validatePlan + ensureMediaSteps 兜底 + intent-suite 回归 |
| LLM 编造 agentId | validatePlan 强制 agentId ∈ AgentCardRegistry |
| Artifact 改造影响面大 | sendLegacyArtifacts 兼容 + 分 Agent 逐步迁移 |
| ai-server 不可用 | Tool 失败返回明确错误；控制台 ai ping |
| 测试与线上一致性 | learning-loop 测试走 QuizService 同路径 |
| 双轨 Orchestrator | 固定 use-agentic-pipeline=false |

---

## 12. 实施顺序（推荐）

```
Week 1   Sprint 1  测试观测层（OrchestratorTestService + 控制台 + REST）
Week 2   Sprint 2  Artifact 统一（ChatRouteDispatcher + 核心 Agent）
Week 3   Sprint 3  AgentCard 补全 + 删 CapabilityRegistry
Week 4   Sprint 4  retry + learning_loop 测试 + intent-suite
Week 5+  前端对接（按第 9 节清单）
```

---

## 13. 复盘结论

| 维度 | 结论 |
|------|------|
| **架构方向** | ✅ 正确：backend 编排 + ai-server 能力 SDK + 自研前端 |
| **主路径选择** | ✅ 正确：Java Orchestrator（非 ai-server Agentic Pipeline） |
| **当前完成度** | 后端 Sprint 1–3 ✅；Sprint 4 待做；前端协议对齐 ~60% |
| **最大缺口** | Pipeline retry、learning_loop 测试对齐、前端 pipelineTaskApi |
| **实施策略** | ✅ 先测后改：Sprint 1–3 已完成 |
| **文档一致性** | ARCHITECTURE_UPGRADE.md 仍写 Vue/TS，应以本文档 + 实际 React 为准 |

**定稿：可按本文档 Sprint 4 继续实施。**

---

## 附录 C：Sprint 1–3 落地记录（2026-06-23）

### Sprint 1

- `OrchestratorTestService` + DTO + Support
- `/api/test/plan|pipeline|agent-cards|aiserver-tools|capability/{name}|learning-loop|intent-suite|pipeline-task/*`
- `ConsoleControlPanel` 新增 plan / pipeline / agents / tools / capability / loop / intent / pipeline-status
- `AdminController /agent-test` 返回 planType、steps、stepResults、artifacts
- `PipelineEngine` 步骤结果写入 `artifacts` 字段（便于测试观测）

### Sprint 2

- `ArtifactExtractor` + `StepStreamPolicy` 统一 WS 推送
- `ChatRouteDispatcher` 移除 `agentId.contains` 硬编码，按 `ArtifactKind` 分发
- `PipelineResult.failure()` 保留 stepResults；前端 `flushPendingChunks` + quiz/image 成功判定
- 图片/Quiz metadata 持久化修复

### Sprint 3

- 7 个 Agent 补全 `getAgentCard()`（Lesson / Resource / Difficulty / KnowledgeState / LearningStyle / SpacedRepetition / Intervention）
- `AgentCardRegistry` 扩展 `findByTag` / `findByKeyword` / `matchBestAgent` / `buildHelpMessage`
- `OrchestratorCore` 移除 `registerAgentCapabilities()`；HELP/简单路由走 `AgentCardRegistry`
- **已删除** `CapabilityRegistry.java`、`AgentCapability.java`、`CapabilityRegistryTest.java`
- 新增 `AgentCardRegistryTest.java`

测试见 README 或本文档第 8 节。

---

## 附录 A：关键代码路径

```
backend/src/main/java/com/lqragent/backend/
├── orchestrator/
│   ├── OrchestratorCore.java          # 调度中枢
│   ├── planning/PlanningAgent.java    # 任务规划 v4
│   ├── pipeline/PipelineEngine.java   # DAG 执行
│   ├── quality/QualityGate.java       # 质检
│   ├── card/AgentCardRegistry.java    # 能力目录
│   └── artifact/Artifact.java         # 统一产物
├── chat/
│   ├── handler/ChatWebSocketHandler.java
│   ├── handler/ChatRouteDispatcher.java  # Artifact 按 kind 分发
│   └── proxy/AiServerWsProxy.java
├── agents/                            # 20 个业务 Agent
├── admin/controller/
│   ├── AgentTestController.java       # /api/test/*
│   └── ConsoleControlPanel.java       # plan/pipeline/agents 等
└── quiz/service/QuizService.java      # learning_loop 触发点

frontend/src/
├── utils/hooks/wsMessageDispatcher.ts
├── utils/types/artifact.ts            # 待扩展
├── api/student/pipeline.ts            # 已有，未使用
└── components/admin/dev-console/      # 管理后台测试
```

## 附录 B：相关文档

| 文档 | 说明 |
|------|------|
| ARCHITECTURE_UPGRADE.md | 8 阶段施工手册（部分已完成） |
| docs/LQRAgent_技术分析报告.md | 痛点与优化进度 |
| README.md | 启动与环境要求 |

---

*文档结束*
