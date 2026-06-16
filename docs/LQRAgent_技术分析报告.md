# LQRAgent 下一步优化计划书

> 更新日期：2026-06-16（第四轮更新，已完成的标记 ✅，部分完成的标记 🔧）
> 目标：解决痛点、改善体验、提升代码可维护性

---

## 已完成的优化

### ✅ 痛点1：两个 BaseAgent 合并
- 删除了旧版 `agents/base/BaseAgent.java`，只保留 `orchestrator/agents/BaseAgent.java`
- 内部类 `AgentRequest`/`AgentResponse`/`ToolExecution` 提取为 `agents/base/` 下的独立文件
- 统一了 `AgentResponse`，合并了 record 风格（Pipeline用）和 DTO 风格（工具用）两种用法
- `AgentInterface` 接口不再依赖旧版 BaseAgent 的内部类

### ✅ 痛点2：ChatWebSocketHandler 拆分
- Handler 从 895行 精简到 287行，只管连接管理 + 消息解析 + 路由分发
- 路由处理逻辑拆到 `ChatRouteDispatcher`（Pipeline回调、QA回退、Artifact发送）
- 新增 `WsSender` 接口解耦发送逻辑

### ✅ 痛点3：死代码和残留文件清理
删除了 9 个文件：
- `AdminController.java.tmp.*`（编译残留）
- `SearchKnowledgeTool.java`（空壳，已被RagSearchTool替代）
- `core/tool/ToolExecutor.java`、`ToolResult.java`、`ToolSchema.java`（0引用，被AgentTool替代）
- `agents/orchestrator/service/OrchestratorService.java`（旧版意图识别）
- `agents/orchestrator/service/LlmIntentClassifier.java`（同上）
- `agents/orchestrator/dto/IntentResult.java`（同上）

### ✅ 痛点12：isUseAgenticPipeline 硬编码修复
- 从 `return false` 改为读取数据库配置 `ai-server.use-agentic-pipeline`
- 管理后台改配置即可切换，不用改代码重新编译

### ✅ 痛点4：Agent目录组织统一
- 去掉了5个分组层（`check/`、`content/`、`learn/`、`serve/`、`user/`）
- 所有Agent平铺在 `agents/` 下，按模块名组织
- 17个模块完成迁移，45个Java文件的包名和import全部更新
- 迁移映射：`serve/qa/` → `qa/`、`learn/path/` → `path/`、`content/diagram/` → `diagram/` 等

### ✅ 痛点9：useWebSocket 拆分
- `useWebSocket.ts` 从 310行 精简到 109行，只管连接/重连/发送/断开
- 消息分发和artifact处理逻辑拆到 `wsMessageDispatcher.ts`
- 改一个artifact类型不再需要动连接管理代码

### ✅ 痛点10：前端API调用统一
- 删除 `utils/api/chat.ts`（原生fetch风格）
- 新建 `api/student/chat.ts`（统一用axios，自动带token、自动401处理）
- 5个引用文件全部更新

### 🔧 痛点5：新用户空状态引导（部分完成）

**已完成：**
- Dashboard 新用户无数据时显示引导卡片："还没有学习数据，先从聊天开始吧"，带"去聊天规划"和"查看学习路径"按钮
- 学习资源空状态页：去掉"Python基础"误导文字，改为"还未选择知识点"；提示改为"还没有专属资源，先选择知识点或让AI生成路径"；增加"去问AI生成路径"按钮
- 答题空状态页：去掉"Python基础"误导文字，改为"还未生成专属练习"；提示改为"先完成学习路径规划"；按钮改为"去聊天规划学习"
- 聊天页空状态：增强欢迎文案，增加"1.说出目标 2.生成路径 3.学资源并练习"三步引导

**未完成：**
- 首页引导视觉偏弱，目前只是一张提示卡片，没有做强首屏引导效果（大面积欢迎区+主按钮+流程图）
- 聊天页没有自动发送欢迎消息（避免误触发AI请求，但可以考虑显示一条非真实消息的欢迎）

**优先级：** P0 — 基础引导已做，视觉强化后续再做

---

### 🔧 痛点6：学习路径生成进度反馈（后端+前端已完成核心部分）

**已完成：**

后端 — Pipeline 任务持久化 + 进度推送：
- 新建 `pipeline_task` 表，记录每次 Pipeline 执行的 taskId、userId、状态、当前步骤、步骤结果、错误信息
- 新建 `PipelineTaskService`，封装任务创建/步骤更新/完成/失败标记/查询
- 改 `OrchestratorCore.executePipelineAndNotify()`：
  - 创建任务记录（DB: RUNNING）
  - 使用带回调的 `PipelineEngine.execute()`，每步完成时更新 DB + 推送 `agent_step` 事件
  - 完成/失败时标记任务状态
  - `pipeline_start` 事件新增 `taskId` 和 `steps` 列表
- 新建 REST API：
  - `GET /api/pipeline/tasks/{taskId}` — 查询单个任务状态
  - `GET /api/pipeline/tasks/latest` — 查询用户最近任务
  - `GET /api/pipeline/tasks` — 查询用户所有任务
- 前端新建 `api/student/pipeline.ts`（查询任务状态API）

前端 — 进度展示：
- 改 `wsMessageDispatcher.ts`：处理 `pipeline_start`（初始化所有步骤为pending）和后端新格式 `agent_step`（stepId+agentId+success）
- 改 `AgentStepsBar.tsx`：
  - Pipeline 模式显示进度文本："2/5 步完成 · 正在执行：生成路径"
  - 每秒刷新耗时显示（不只有done才显示）
  - 所有步骤显示中文名称（新增 `STEP_LABELS` 映射：profile→获取画像、path_gen→生成路径 等）
- 新增事件类型：`pipeline_start`、`pipeline_complete`、`pipeline_error`

**未完成：**
- 超时提示："已等待XX秒，请耐心等待"
- 步骤并行化（ProfileAgent和ContentAnalysisAgent可以并行，但需要验证数据依赖）
- 刷新后前端恢复进度（API已有，但ChatView重连后还没调）
- Pipeline 失败可重试（阶段3规划中）

**优先级：** P0 — 核心链路已通，用户能看到步骤进度了

---

## 待完成的优化

### 痛点7：意图识别不够准

**现状：** 用户说"帮我学Python"可能被识别为QA问答而不是学习路径。用户说"出几道题"可能被识别为QA而不是资源生成。

**建议：**
- 在PlanningAgent的prompt里加更多示例，特别是容易混淆的场景
- 对"帮我学/教我/我想学"这类关键词，优先路由到学习路径
- 对"出题/练习/测验"这类关键词，优先路由到资源生成

**优先级：** P1 — 聊天是用户第一入口，识别不准很烦

---

### 痛点8：答题功能体验粗糙

**现状：**
- 题目格式依赖LLM输出，格式不对就显示异常
- 没有错题本
- 没有难度分级

**建议：**
- 答题页面加格式容错：如果LLM没输出标准选项，自动补上A/B/C/D
- 加一个简单的"错题回顾"功能（答题记录已经有了，只是没展示）
- 出题时在prompt里指定难度等级

**优先级：** P2 — 功能可用但体验粗糙

---

### 痛点11：AI服务连不上时没有友好提示

**现状：** 如果ai-server没启动或LLM API Key配错，用户在聊天里只会看到"处理异常"或"无法连接AI服务"。

**建议：**
- 后端启动时做一次AI服务健康检查，如果不通就写日志告警
- 前端在连接失败时显示具体原因："AI服务未启动，请联系管理员" 或 "模型API Key无效"
- 管理后台的"系统状态"页面显示各服务的连通状态

**优先级：** P0 — 演示时最怕的就是报错看不懂

---

## 新增规划：Pipeline 稳定性升级（从本轮开始）

本轮除了继续修原有痛点，还启动了"Pipeline 稳定性升级"——把学习路径生成从"一次聊天响应"升级成"可追踪任务"。

### ✅ 阶段1：Pipeline 任务持久化 + 状态查询 API
- 新建 `PipelineTask` 实体 + `PipelineTaskRepository` + `PipelineTaskDto` + `PipelineTaskService`
- PipelineEngine 每步完成时通过回调更新 DB
- OrchestratorCore 创建任务记录 + 使用回调 + 推送 agent_step 事件
- 新建 `PipelineTaskController`（查询任务状态 API）
- 前端新建 `api/student/pipeline.ts`

### ✅ 阶段2：前端展示 Pipeline 进度
- wsMessageDispatcher 处理 pipeline_start / agent_step（新格式）/ pipeline_complete / pipeline_error
- AgentStepsBar 展示步骤中文名 + 进度文本 + 实时耗时
- 新增 STEP_LABELS 映射

### 待做 阶段3：Pipeline 失败可重试
- pipeline_task 表加 failed_step 字段
- PipelineEngine 支持"从某一步开始执行"
- 新增 `POST /api/pipeline/tasks/{taskId}/retry` API
- 前端聊天区增加失败提示和重试按钮

### 待做 阶段4：PlanningAgent 意图识别加强
- prompt 加更多易混淆示例
- Function Calling tool description 加硬规则提示
- 建一个意图识别测试集（至少30条）

### 待做 阶段5：Agent 输出结构化校验
- 为关键 Agent 输出定义 JSON Schema
- GeneratePathTool / GenerateResourceTool 输出前校验
- 校验不过重试，重试还不过返回明确错误

---

## 优先级排序（更新版）

| 优先级 | 编号 | 内容 | 状态 | 理由 |
|--------|------|------|------|------|
| P0 | 痛点5 | 新用户空状态引导 | 🔧 部分完成 | 基础引导已做，视觉强化后续做 |
| P0 | 痛点6 | 路径生成进度反馈 | 🔧 核心完成 | 后端持久化+前端进度条已通，超时提示/重试待做 |
| P0 | 痛点11 | AI服务断连友好提示 | 待做 | 演示时最怕的就是报错看不懂 |
| P1 | 痛点7 | 改进意图识别 | 待做 | 聊天是用户第一入口，识别不准很烦 |
| P2 | 痛点8 | 答题体验优化 | 待做 | 功能可用但体验粗糙 |
| P1 | Pipeline阶段3 | 失败可重试 | 待做 | 某步失败不需要重新来一遍 |
| P1 | Pipeline阶段4 | 意图识别加强 | 待做 | 减少误判 |
| P1 | Pipeline阶段5 | 输出结构化校验 | 待做 | 数据更稳 |
| ~~P1~~ | ~~痛点1~~ | ~~合并两个BaseAgent~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点2~~ | ~~拆分ChatWebSocketHandler~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点3~~ | ~~清理死代码和残留文件~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点12~~ | ~~配置项从硬编码改数据库读~~ | ✅ 已完成 | |
| ~~P2~~ | ~~痛点4~~ | ~~统一Agent目录组织~~ | ✅ 已完成 | |
| ~~P2~~ | ~~痛点9~~ | ~~拆分useWebSocket~~ | ✅ 已完成 | |
| ~~P2~~ | ~~痛点10~~ | ~~统一API调用方式~~ | ✅ 已完成 | |

---

## 下一步行动建议

12个痛点已解决7个，2个部分完成，3个待做。Pipeline稳定性升级已完成前2个阶段。

**推荐顺序：**

1. **痛点11：AI服务断连友好提示**（P0，最后一块 P0 短板，只改前端+加健康检查）
2. **Pipeline阶段3：失败可重试**（P1，补全任务闭环）
3. **痛点7 + Pipeline阶段4：意图识别加强**（P1，合并做，改 prompt + 加测试集）
4. **Pipeline阶段5：输出结构化校验**（P1，让数据更稳）
5. **痛点8：答题体验优化**（P2，有时间再搞）
6. **痛点5：首页视觉强化**（P2，基础引导已做，视觉提升可后补）
