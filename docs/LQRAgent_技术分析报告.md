# LQRAgent 下一步优化计划书

> 更新日期：2026-06-15
> 目标：解决痛点、改善体验、提升代码可维护性

---

## 第一部分：代码结构问题（影响"改得动"的程度）

### 痛点1：两个 BaseAgent，新人看代码会懵

**现状：**
- `agents/base/BaseAgent.java` — 旧版基类，简单的LLM推理循环
- `orchestrator/agents/BaseAgent.java` — 新版基类，加了Redis Streams、能力注册、CFP协商

两个类都叫 `BaseAgent`，都在做LLM推理循环，但功能不同。现在所有业务Agent继承的是**新版**（orchestrator下的），旧版基本没人用了，但还留在代码里。

**问题：** 新人接手时会困惑"该继承哪个？"，而且两套基类导致 `AgentResponse` 也分两套（`agents.base.BaseAgent.AgentResponse` vs 直接构造），到处都是 `@SuppressWarnings("unchecked")` 做类型强转。

**建议：**
- 删掉 `agents/base/BaseAgent.java`，只保留 `orchestrator/agents/BaseAgent.java`
- 把旧版里还有用的逻辑（比如简单的LLM调用）合并到新版里
- 统一 `AgentResponse` 的用法，减少类型强转

### 痛点2：ChatWebSocketHandler 是个850行的上帝类

**现状：** 这个文件干了太多事：
- WebSocket连接管理
- 意图识别后的路由分发
- QA回答流程（含Pipeline和QaAgent回退）
- Pipeline异步执行+进度推送
- 学习路径生成
- 图片/视频生成
- 画像更新触发
- 各种artifact事件的组装和发送

**问题：** 改任何一个功能都要动这个文件，风险极高。而且代码里大量嵌套的lambda和异步回调，可读性很差。

**建议：** 按功能拆分：
- `QaRouteHandler` — 处理QA问答
- `PipelineRouteHandler` — 处理Pipeline类请求（学习路径等）
- `ArtifactSender` — 统一处理artifact事件的组装和发送
- `ChatWebSocketHandler` 只做连接管理 + 路由分发

### 痛点3：死代码和残留文件没清理

**现状：**
- `AdminController.java.tmp.32224.558c988c99e9` — 一个临时文件残留在代码里
- `agents/serve/qa/tools/SearchKnowledgeTool.java` — 之前被RagSearchTool替代了，但文件还在
- `agents/orchestrator/` 下有 `OrchestratorService.java` 和 `LlmIntentClassifier.java`，看起来是旧版意图识别的残留，现在用的是 `orchestrator/` 下的 `PlanningAgent`
- `core/tool/` 下的 `ToolExecutor`、`ToolResult`、`ToolSchema` 与 `agents/base/AgentTool` 功能重复

**建议：** 逐一确认是否还有引用，没引用的直接删掉。特别是 `.tmp` 文件必须清掉，会被打进jar包。

### 痛点4：Agent目录组织不一致

**现状：** Agent的目录结构不统一：
- 有的按"功能域"组织：`learn/`、`content/`、`serve/`、`check/`
- 有的按"模块"组织：`mediageneration/`、`resourcegeneration/`、`learnerprofile/`、`qualityassessment/`
- `effectassessment/` 放在 `agents/` 下但只有service没有Agent类

**问题：** 找代码靠猜。比如"质量评估"是 `check/QualityAgent` 还是 `qualityassessment/QualityAssessmentService`？两个都有，干的还不完全是一件事。

**建议：** 统一目录组织原则，要么全按功能域分，要么全按模块分。推荐按模块分（每个模块自己的Agent+Service+Controller+Entity都在一个包下），因为现在大部分新代码已经是这个模式了。

---

## 第二部分：功能体验问题（影响"好不好用"的程度）

### 痛点5：新用户第一次进来什么都看不到

**现状：** Dashboard、资源页、答题页，新用户看到的都是空白。没有任何引导告诉用户"你需要先做什么"。

**建议：**
- 每个空状态页面加引导卡片，比如：
  - Dashboard: "还没有学习数据，去聊天试试吧"
  - 资源页: "先选择一个知识点，或者让AI帮你生成学习路径"
  - 答题页: "先完成学习路径规划，才能开始练习"
- 聊天页面作为入口，用户第一次进来自动发一个欢迎消息

### 痛点6：学习路径生成太慢，用户以为卡死了

**现状：** 5步Pipeline串行，每步调LLM，总耗时经常超过60秒。前端只显示"正在处理，请稍候..."，没有任何进度反馈。

**建议：**
- 前端显示当前执行到第几步（已有agent_step事件，但没有用起来显示步骤名称）
- 考虑把一些步骤并行化（ProfileAgent和ContentAnalysisAgent可以并行）
- 加一个超时提示："已等待XX秒，请耐心等待"或"生成时间较长，建议稍后查看"

### 痛点7：意图识别不够准

**现状：** 用户说"帮我学Python"可能被识别为QA问答而不是学习路径。用户说"出几道题"可能被识别为QA而不是资源生成。

**建议：**
- 在PlanningAgent的prompt里加更多示例，特别是容易混淆的场景
- 对"帮我学/教我/我想学"这类关键词，优先路由到学习路径
- 对"出题/练习/测验"这类关键词，优先路由到资源生成

### 痛点8：答题功能体验粗糙

**现状：**
- 题目格式依赖LLM输出，格式不对就显示异常
- 没有错题本
- 没有难度分级

**建议：**
- 答题页面加格式容错：如果LLM没输出标准选项，自动补上A/B/C/D
- 加一个简单的"错题回顾"功能（答题记录已经有了，只是没展示）
- 出题时在prompt里指定难度等级

---

## 第三部分：前后端配合问题

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

### 痛点10：前端API层和聊天WebSocket的调用方式不统一

**现状：** 
- REST API调用走 `api/student/*.ts`（axios）
- 聊天走 `utils/api/chat.ts`（也是axios）
- 但WebSocket通信走 `utils/hooks/useWebSocket.ts`（原生WebSocket）
- 学习路径走 `utils/hooks/useOrchestrator.ts`（另一个WebSocket）

**问题：** 三个不同的通信方式，状态管理也分散在不同store里。

**建议：** 至少把 `utils/api/chat.ts` 和 `api/student/` 下的API调用统一到 `api/` 目录下。

---

## 第四部分：配置和部署问题（影响"能不能跑起来"）

### 痛点11：AI服务连不上时没有友好提示

**现状：** 如果ai-server没启动或LLM API Key配错，用户在聊天里只会看到"处理异常"或"无法连接AI服务"。

**建议：**
- 后端启动时做一次AI服务健康检查，如果不通就写日志告警
- 前端在连接失败时显示具体原因："AI服务未启动，请联系管理员" 或 "模型API Key无效"
- 管理后台的"系统状态"页面显示各服务的连通状态

### 痛点12：isUseAgenticPipeline() 被硬编码为false

**现状：** `AppRuntimeConfig.isUseAgenticPipeline()` 里面直接 `return false`，注释说"手动改此处为true"才能切到ai-server路径。这意味着这个配置实际上没有生效，改了数据库配置也没用。

**建议：** 改成从数据库读取，跟其他配置一样走 `sysConfigService.getValue()`。这样管理后台改配置就能切换，不用改代码重新编译。

---

## 优化优先级排序

| 优先级 | 编号 | 内容 | 理由 |
|--------|------|------|------|
| P0 | 痛点5 | 新用户空状态引导 | 演示第一印象，不做等于白搭 |
| P0 | 痛点6 | 路径生成进度反馈 | 等60秒没反馈 = 用户以为坏了 |
| P0 | 痛点11 | AI服务断连友好提示 | 演示时最怕的就是报错看不懂 |
| P1 | 痛点1 | 合并两个BaseAgent | 不清掉后面改代码容易改错地方 |
| P1 | 痛点2 | 拆分ChatWebSocketHandler | 不拆的话每次改功能都提心吊胆 |
| P1 | 痛点3 | 清理死代码和残留文件 | 减少新人理解成本 |
| P1 | 痛点7 | 改进意图识别 | 聊天是用户第一入口，识别不准很烦 |
| P1 | 痛点12 | 配置项从硬编码改数据库读 | 演示时方便切换，不用重启 |
| P2 | 痛点4 | 统一Agent目录组织 | 不急但不做的话越往后越难改 |
| P2 | 痛点8 | 答题体验优化 | 功能可用但体验粗糙 |
| P2 | 痛点9 | 拆分useWebSocket | 前端可维护性 |
| P2 | 痛点10 | 统一API调用方式 | 前端一致性 |

---

## 预计改动范围

- **P0（体验类）**：只改前端，不动后端逻辑，风险低
- **P1（结构类）**：主要改后端Java代码，需要仔细测试各Agent是否正常
- **P2（优化类）**：可做可不做，看时间

建议先做P0，让演示效果好看；再做P1，让代码改得动；P2有时间再搞。
