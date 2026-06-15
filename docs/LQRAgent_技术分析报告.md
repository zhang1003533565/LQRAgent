# LQRAgent 下一步优化计划书

> 更新日期：2026-06-15（第三轮更新，已完成的标记 ✅）
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

---

## 待完成的优化

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
| P2 | 痛点8 | 答题体验优化 | 待做 | 功能可用但体验粗糙 |
| ~~P1~~ | ~~痛点1~~ | ~~合并两个BaseAgent~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点2~~ | ~~拆分ChatWebSocketHandler~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点3~~ | ~~清理死代码和残留文件~~ | ✅ 已完成 | |
| ~~P1~~ | ~~痛点12~~ | ~~配置项从硬编码改数据库读~~ | ✅ 已完成 | |
| ~~P2~~ | ~~痛点4~~ | ~~统一Agent目录组织~~ | ✅ 已完成 | |
| ~~P2~~ | ~~痛点9~~ | ~~拆分useWebSocket~~ | ✅ 已完成 | |
| ~~P2~~ | ~~痛点10~~ | ~~统一API调用方式~~ | ✅ 已完成 | |

---

## 下一步行动建议

12个痛点已解决7个，剩余5个。地基和代码结构类问题全部搞完了，剩下的都是**用户可感知的体验优化**：

1. **P0 三件事**（先做）：空状态引导、路径生成进度、AI断连提示 — 只改前端，不动后端逻辑，风险低
2. **P1 意图识别**：改进PlanningAgent的prompt，让聊天路由更准确
3. **P2 答题体验**：格式容错、错题回顾，有时间再搞
