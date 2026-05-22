# 软件杯项目开发说明 v2

## 1. 项目定位

本项目对应题目《基于大模型的多智能体个性化学习资源生成与导航系统》，比赛场景聚焦在一门具体课程上，当前建议课程为《高级语言程序设计 Python》。

这次开发的核心不是重做一个通用大模型平台，而是基于现有 `ai-server` 能力，快速实现一个适合软件杯展示的课程学习系统，突出四件事：

- 多智能体协同
- 个性化学习路径
- 资源自动生成
- 基于行为反馈的动态调整

## 2. 你们现在确认下来的产品思路

根据当前讨论，比赛版产品形态统一为：

- 前端是 React 写的聊天式工作台，风格参考开源项目现有 Web 端
- 用户登录后自动判断角色，进入不同页面
- 系统同时存在公共课程底座和个人扩展内容
- 个人上传资料不做同步即时处理，而是进入后台排队异步处理

这四点会直接影响架构、数据表和分工，后面都按这个思路展开。

## 3. 先把三个概念分清

项目里最容易混淆的是“知识图谱”和“知识库”。比赛版必须拆成三层：

### 3.1 公共知识图谱

这部分是你们自己整理好的课程结构，属于结构化教学骨架。

它负责：

- 章节划分
- 知识点定义
- 前置依赖关系
- 学习路径规划的顺序依据

它应该放在 `backend` 管理，不放在 `ai-server` 里。

### 3.2 公共知识库

这部分是你们提前准备好的课程资料，属于 RAG 的公共输入。

它负责：

- 课程讲义
- 典型例题
- 代码示例
- 扩展阅读材料

它应该进入 `ai-server` 的知识库体系，供检索和生成时使用。

### 3.3 个人知识库

这部分是用户自己上传的资料，属于个性化扩展内容。

它负责：

- 用户个性化补充资料
- 用户自己的笔记、作业、外部文档
- 个性化答疑和资源生成的附加上下文

这部分同样进入 `ai-server` 的知识库体系，但处理方式必须是异步排队。

### 3.4 三者分工结论

- 公共知识图谱负责“导航”
- 公共知识库负责“课程内容检索”
- 个人知识库负责“个性化补充检索”

不要把三者混成一个模块。

## 4. 总体架构

### 4.1 三层职责

#### `frontend`

负责：

- 登录与角色跳转
- 聊天式主界面
- 学习路径展示
- 资源卡片展示
- 答题交互
- 上传资料与队列状态展示

#### `backend`

负责：

- 用户与角色
- 学生画像
- 公共知识图谱
- 学习路径规划
- 学习行为记录
- 上传任务队列
- 对 `ai-server` 的统一调用封装

#### `ai-server`

负责：

- 大模型调用
- 会话和流式交互
- 公共知识库与个人知识库入库检索
- 资源生成
- 题目生成
- 答疑与解释
- 记忆增强

### 4.2 推荐调用链

1. 前端发起学习请求、提问、上传、答题结果。
2. `backend` 先查角色、画像、图谱、历史记录。
3. `backend` 判断本次属于哪一类任务。
4. 需要生成或检索时，调用 `ai-server`。
5. `backend` 收敛结果，更新画像、路径、行为日志。
6. 前端展示内容，并给出下一步建议。

## 5. ai-server 里已经可以直接复用的部分

下面这些能力你们不要重做，直接复用或薄改：

### 5.1 统一运行时与流式入口

- `ai-server/deeptutor/app/facade.py`
- `ai-server/deeptutor/api/routers/unified_ws.py`
- `ai-server/deeptutor/services/session/turn_runtime.py`
- `ai-server/deeptutor/runtime/registry/capability_registry.py`

用途：

- 统一 turn 级别执行
- WebSocket 流式返回
- 能力注册与调用

### 5.2 聊天与答疑基础

- `ai-server/deeptutor/api/routers/chat.py`
- `ai-server/deeptutor/api/routers/solve.py`
- `ai-server/deeptutor/agents/solve/`

用途：

- 普通对话
- 课程内答疑
- 过程解释与结构化回答

### 5.3 知识库与 RAG

- `ai-server/deeptutor/api/routers/knowledge.py`
- `ai-server/deeptutor/services/rag/service.py`

用途：

- 文档上传
- 建知识库
- 检索内容
- 为生成结果提供事实支撑

### 5.4 资源结构化生成

- `ai-server/deeptutor/api/routers/book.py`
- `ai-server/deeptutor/book/engine.py`
- `ai-server/deeptutor/book/agents/`

用途：

- 组织讲义
- 结构化生成知识页面
- 形成资源块和学习内容片段

### 5.5 题目生成

- `ai-server/deeptutor/api/routers/question.py`
- `ai-server/deeptutor/agents/question/`

用途：

- 练习题生成
- 跟进题生成
- 题目相关反馈

### 5.6 会话与记忆

- `ai-server/deeptutor/api/routers/sessions.py`
- `ai-server/deeptutor/api/routers/memory.py`
- `ai-server/deeptutor/services/memory/service.py`

用途：

- 留存会话历史
- 记录用户偏好和上下文
- 增强后续回答

### 5.7 结论

比赛项目真正需要你们自己重点开发的，不是大模型底层，而是：

- 课程数据层
- 图谱导航层
- 画像与策略层
- 队列与业务流程层
- React 前端交互层

## 6. 不要照搬开源项目前端，要做比赛版 React 工作台

### 6.1 页面结构

比赛版前端建议采用 React 单页应用，页面只保留核心场景：

- 登录页
- 学生工作台
- 我的资料页
- 学习进度/画像页
- 管理页

### 6.2 学生工作台布局

学生登录后默认进入聊天式首页，页面建议分三栏：

- 左侧：会话与功能入口
- 中间：聊天与流式输出主区域
- 右侧：学习路径、资源卡片、推荐动作

### 6.3 学生工作台要有的组件

- `ChatPanel`
- `StreamingMessage`
- `LearningPathPanel`
- `ResourceTabs`
- `QuizPanel`
- `UploadQueuePanel`
- `ProfileSummaryCard`

### 6.4 角色跳转规则

登录成功后，根据角色自动跳转：

- `student`
  - 进入学生工作台
- `teacher`
  - 进入课程维护/教学视图
- `admin`
  - 进入系统管理页

### 6.5 角色对应页面

#### 学生页

展示：

- 聊天式学习入口
- 学习路径
- 资源内容
- 答题反馈
- 个人上传任务状态

#### 教师页

展示：

- 公共课程知识图谱查看
- 公共知识库维护
- 示例资源预览

#### 管理页

展示：

- 用户管理
- 角色管理
- 上传任务队列状态
- 系统健康状态

## 7. 你们自己的 backend 才是比赛版业务中台

### 7.1 backend 需要新增的能力

建议在 `backend` 中建立这些模块：

- `auth`
- `user`
- `profile`
- `knowledge-graph`
- `learning-path`
- `resource-facade`
- `assessment`
- `upload-queue`
- `aI-server-client`

### 7.2 backend 的核心职责

#### 用户与角色

负责：

- 登录鉴权
- 角色识别
- 登录后路由信息返回

#### 学生画像

负责：

- 结构化画像字段存储
- 从聊天记录和答题结果更新画像
- 为路径规划提供输入

#### 公共知识图谱

负责：

- 维护课程章节
- 维护知识点
- 维护前置依赖关系
- 提供路径搜索函数

#### 学习路径规划

负责：

- 根据当前知识状态和目标知识点找路径
- 决定每个步骤需要什么资源
- 接收效果评估结果并动态调整

#### 上传任务队列

负责：

- 接收个人上传请求
- 入队
- 异步处理
- 状态回传

#### ai-server 调用封装

负责：

- 封装聊天、知识库、资源生成、出题、答疑接口
- 隔离前端与 `ai-server` 的耦合

## 8. 上传资料不即时处理，而是排队异步处理

这是你刚刚确认的关键需求，建议直接作为比赛亮点写进架构说明。

### 8.1 为什么不能同步处理

因为文档上传、解析、切分、嵌入、建索引本身耗时较长，如果同步做：

- 用户等待时间长
- 页面体验差
- 前端状态复杂
- 比赛演示容易卡住

### 8.2 正确做法

前端上传后：

1. `backend` 先落一条上传任务记录。
2. 返回“已排队”状态。
3. 后台 worker 慢慢处理。
4. 处理完成后，知识库状态更新。
5. 前端轮询或 WebSocket 刷新状态。

### 8.3 队列表建议

建议建立表：

- `kb_upload_task`

字段建议：

- `id`
- `user_id`
- `file_name`
- `kb_scope`
- `status`
- `priority`
- `error_message`
- `created_at`
- `started_at`
- `finished_at`

### 8.4 `kb_scope` 建议值

- `PUBLIC`
- `PERSONAL`

### 8.5 `status` 建议值

- `PENDING`
- `PROCESSING`
- `COMPLETED`
- `FAILED`

### 8.6 第一版实现建议

第一版不一定非上 Redis/RabbitMQ，可以先做：

- 数据库任务表
- Spring Boot 定时轮询 worker
- 单机顺序处理

如果后面要增强展示，再抽象出 `TaskBus` 接口接 Redis。

## 9. 按文档逐模块怎么开发

## 9.1 课程与数据准备

### 该做什么

- 固定 Python 课程章节
- 产出 6 章以上知识点
- 维护 30+ 知识点及依赖关系
- 准备公共知识库种子文件

### 应放在哪里

- 图谱：`backend` 对应数据目录和数据库
- 公共知识库：交给 `ai-server` 入库

### 需要产出

- `backend/src/main/resources/course/outline.json`
- `backend/src/main/resources/course/knowledge_graph.json`
- `backend/src/main/resources/course/seed_kb/...`
- `backend/src/main/resources/course/examples/...`

### 验收重点

- 路径规划能用图谱算顺序
- 资源生成能用种子知识库取内容

## 9.2 多智能体协同框架

### 该做什么

- 不重写大框架
- 用 `backend` 做比赛版 Orchestrator
- 用 `ai-server` 做 AI 执行层

### 你们自己要写

- `AiServerClient`
- `OrchestratorService`
- `LearningController`
- `UploadQueueService`

### 验收重点

- 前端请求能统一进入 `backend`
- `backend` 能按意图路由到不同 AI 能力
- 所有 AI 结果都能回收到统一格式

## 9.3 学生画像智能体 + 知识图谱模块

### 学生画像怎么做

`ai-server` 的 memory 只作为文本型记忆增强，不替代你们自己的结构化画像。

画像表至少保留这些字段：

- `knowledge_level`
- `learning_goal`
- `cognitive_style`
- `common_errors`
- `learning_pace`
- `interest_direction`
- `preferred_resource_type`

### 知识图谱模块怎么做

放在 `backend`，至少实现：

- `getPrerequisites(kpId)`
- `findLearningPath(current, target)`
- `recommendNextPoints(profile, currentState)`

### 验收重点

- 能从用户表达中抽取画像信息
- 能根据图谱返回学习顺序

## 9.4 资源生成智能体 + 质量评估智能体

### 资源生成应该怎么拆

不要做一个超大单体生成器，建议拆成几个资源生产函数：

- `generateLessonMarkdown`
- `generateMindMapData`
- `generateQuiz`
- `generateReadingSummary`
- `generateCodeCase`

### 推荐复用 ai-server 的部分

- `book`
- `question`
- `chat`
- `solve`

### 质量评估怎么做

建议“检索校验 + 规则校验 + LLM 自检”三段式：

- 事实校验
- 敏感内容过滤
- 表达规范检查

### 失败重试机制

- 初稿生成
- 评估
- 不通过则带原因重试一次
- 仍不通过则标记失败

## 9.5 路径规划 + 效果评估 + 答疑

### 路径规划应该怎么做

放在 `backend`，而不是完全放给大模型。

建议流程：

1. 图谱先算候选路径。
2. 画像决定节奏和资源偏好。
3. 大模型把结构化路径写成自然语言学习计划。

### 效果评估应该怎么做

第一版以规则和指标为主：

- 正确率
- 点击次数
- 停留时间
- 重复追问点
- 连续失败次数

再让大模型基于这些指标生成评估报告。

### 路径如何动态调整

可以先实现四条规则：

- 正确率低，插入复习节点
- 编程题连续错，补代码案例
- 概念题错多，补讲解文档
- 某方向兴趣高，补拓展阅读

### 答疑应该怎么做

直接复用：

- `knowledge`
- `chat`
- `solve`

输出建议支持：

- 文本
- 代码
- Mermaid

## 10. React 前端的实际开发建议

### 10.1 页面

- `/login`
- `/workspace`
- `/workspace/upload`
- `/workspace/profile`
- `/admin`
- `/teacher`

### 10.2 核心状态

前端至少维护这些状态：

- 当前用户与角色
- 当前会话
- 当前路径
- 当前知识点
- 当前资源集合
- 上传任务队列状态

### 10.3 与后端的交互方式

- 普通业务请求走 HTTP
- 聊天和流式生成走 WebSocket
- 上传任务状态先走轮询，后续可扩展 WebSocket

## 11. 数据表建议

比赛第一版建议至少建立这些表：

- `user`
- `role`
- `learner_profile`
- `learning_goal`
- `knowledge_point`
- `knowledge_edge`
- `learning_path`
- `learning_path_step`
- `resource_item`
- `quiz_record`
- `study_behavior`
- `path_adjustment_log`
- `kb_upload_task`

## 12. 推荐分工

如果是 6 到 7 人团队，推荐：

- 1 人：课程数据与公共知识图谱
- 1 人：后端鉴权、角色与 API 聚合
- 1 人：画像与知识图谱服务
- 1 人：上传队列与知识库接入
- 1 人：资源生成与质量评估
- 1 人：路径规划与效果评估
- 1 到 2 人：React 前端

如果只有 4 到 5 人团队，推荐：

- A：课程数据 + 图谱
- B：后端业务中台 + 队列
- C：AI 资源生成 + 评估
- D：路径规划 + 画像
- E：React 前端

## 13. 地基阶段应该先做什么

真正的地基阶段，不是马上做炫技页面，而是先做四件事：

### 13.1 固定课程底座

- 确定章节
- 确定知识点 ID
- 确定知识依赖关系
- 准备公共知识库种子文件

### 13.2 固定角色模型

- `student`
- `teacher`
- `admin`

并明确每个角色进哪个页面。

### 13.3 固定业务接口

至少定下这些接口：

- 登录
- 获取当前用户
- 获取学习路径
- 获取某知识点资源
- 提交答题结果
- 上传个人资料
- 查询上传任务状态

### 13.4 固定目录和数据规范

- 图谱文件放哪里
- 公共知识库放哪里
- 个人上传文件怎么命名
- 前后端 DTO 字段怎么命名

## 14. MVP 范围

比赛第一版最小闭环定义如下：

1. 学生登录进入聊天式工作台。
2. 输入学习目标，例如“学习 Python 装饰器”。
3. 系统根据公共知识图谱生成学习路径。
4. 点击路径节点后，系统生成讲解文档、题目、代码案例。
5. 学生提交题目结果。
6. 系统更新画像，并调整下一步推荐。
7. 学生可继续追问，系统结合公共知识库和个人知识库回答。

只要这 7 步稳定跑通，你们的软件杯演示就成立了。
