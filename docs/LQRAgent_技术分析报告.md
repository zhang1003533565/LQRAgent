# LQRAgent 下一步优化计划书

> 更新日期：2026-06-15（第二轮更新，已完成的标记 ✅）
> 目标：解决痛点、改善体验、提升代码可维护性

---

## 已完成的优化（本轮地基整理）

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

---

## 待完成的优化

### 痛点4：Agent目录组织不一致

**现状：** Agent的目录结构不统一：
- 有的按"功能域"组织：`learn/`、`content/`、`serve/`、`check/`
- 有的按"模块"组织：`mediageneration/`、`resourcegeneration/`、`learnerprofile/`、`qualityassessment/`
- `effectassessment/` 放在 `agents/` 下但只有service没有Agent类

**问题：** 找代码靠猜。比如"质量评估"是 `check/QualityAgent` 还是 `qualityassessment/QualityAssessmentService`？两个都有，干的还不完全是一件事。

**建议：** 统一目录组织原则，要么全按功能域分，要么全按模块分。推荐按模块分（每个模块自己的Agent+Service+Controller+Entity都在一个包下），因为现在大部分新代码已经是这个模式了。

**优先级：** P2 — 不影响运行，但越往后越难改

---

### 痛点5：新用户第一次进来什么都看不到

**现状：** Dashboard、资源页、答题页，新用户看到的都是空白。没有任何引导告诉用户"你需要先做什么"。

**建议：**
- 每个空状态页面加引导卡片，比如：
  - Dashboard: "还没有学习数据，去聊天试试吧"
  - 资源页: "先选择一个知识点，或者让AI帮你生成学习路径"
  - 答题页: "先完成学习路径规划，才能开始练习"
- 聊天页面作为入口，用户第一次进来自动发一个欢迎消息

**优先级：** P0 — 演示第一印象，不做等于白搭

---

### 痛点6：学习路径生成太慢，用户以为卡死了

**现状：** 5步Pipeline串行，每步调LLM，总耗时经常超过60秒。前端只显示"正在处理，请稍候..."，没有任何进度反馈。

**建议：**
- 前端显示当前执行到第几步（已有agent_step事件，但没有用起来显示步骤名称）
- 考虑把一些步骤并行化（ProfileAgent和ContentAnalysisAgent可以并行）
- 加一个超时提示："已等待XX秒，请耐心等待"或"生成时间较长，建议稍后查看"

**优先级：** P0 — 等60秒没反馈 = 用户以为坏了

---

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

### 痛点9：useWebSocket 的 hook 写在 utils 里但包含了大量业务逻辑

**现状：** `useWebSocket.ts` 有310行，里面包含了：
- WebSocket连接管理
- 消息分发（chunk/agent_step/artifact/profile_patch/done/error）
- artifact处理逻辑（学习路径/图表/多卡片/RAG来源/图片）
- 自动重连
- 消息发送

**问题：** 这个hook既管连接又管业务，改一个artifact类型就要动这个文件。

**建议：** 把消息分发和artifact处理抽出来：
- `useWebSocket` 只管连接/重连/发送
- `useChatMessageDispatcher` 管消息分发
- artifact处理逻辑移到各自的store或单独的handler里

**优先级：** P2 — 前端可维护性

---

### 痛点10：前端API层和聊天WebSocket的调用方式不统一

**现状：**
- REST API调用走 `api/student/*.ts`（axios）
- 聊天走 `utils/api/chat.ts`（也是axios）
- 但WebSocket通信走 `utils/hooks/useWebSocket.ts`（原生WebSocket）
- 学习路径走 `utils/hooks/useOrchestrator.ts`（另一个WebSocket）

**问题：** 三个不同的通信方式，状态管理也分散在不同store里。

**建议：** 至少把 `utils/api/chat.ts` 和 `api/student/` 下的API调用统一到 `api/` 目录下。

**优先级：** P2 — 前端一致性

---

### 痛点11：AI服务连不上时没有友好提示

**现状：** 如果ai-server没启动或LLM API Key配错，用户在聊天里只会看到"处理异常"或"无法连接AI服务"。

**建议：**
- 后端启动时做一次AI服务健康检查，如果不通就写日志告警
- 前端在连接失败时显示具体原因："AI服务未启动，请联系管理员" 或 "模型API Key无效"
- 管理后台的"系统状态"页面显示各服务的连通状态

**优先级：** P0 — 演示时最怕的就是报错看不懂

---

## 优先级排序（更新版）

| 优先级 | 编号 | 内容 | 状态 | 理由 |
|--------|------|------|------|------|
| P0 | 痛点5 | 新用户空状态引导 | 待做 | 演示第一印象，不做等于白搭 |
| P0 | 痛点6 | 路径生成进度反馈 | 待做 | 等60秒没反馈 = 用户以为坏了 |
| P0 | 痛点11 | AI服务断连友好提示 | 待做 | 演示时最怕的就是报错看不懂 |
| P1 | 痛点7 | 改进意图识别 | 待做 | 聊天是用户第一入口，识别不准很烦 |
| P2 | 痛点4 | 统一Agent目录组织 | 待做 | 不急但不做的话越往后越难改 |
| P2 | 痛点8 | 答题体验优化 | 待做 | 功能可用但体验粗糙 |
| P2 | 痛点9 | 拆分useWebSocket | 待做 | 前端可维护性 |
| P2 | 痛点10 | 统一API调用方式 | 待做 | 前端一致性 |
| ~~P1~~ | ~~痛点1~~ | ~~合并两个BaseAgent~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点2~~ | ~~拆分ChatWebSocketHandler~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点3~~ | ~~清理死代码和残留文件~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点12~~ | ~~配置项从硬编码改数据库读~~ | ✅ 已完成 | |

---

## 下一步行动建议

地基已经打好，接下来进入**用户可感知的功能优化**：

1. **P0 三件事**（先做）：空状态引导、路径生成进度、AI断连提示 — 这些只改前端，不动后端逻辑，风险低
2. **P1 意图识别**：改进PlanningAgent的prompt，让聊天路由更准确
3. **P2 有时间再搞**：Agent目录整理、答题体验、前端代码优化
