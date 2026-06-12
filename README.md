# LQRAgent

面向《Python 高级语言程序设计》的个性化 AI 学习系统（第十五届软件杯 A3 赛题）。

## 启动（只需两步）

```
终端 1:  cd backend && mvn spring-boot:run    # 后端 + ai-server 自动拉起
终端 2:  cd frontend && npm run dev            # 前端开发服务器
```

打开 **http://localhost:5173**，用 `admin` / `123456` 登录 → 在管理后台配置大模型 Key → 开始使用。

**不需要手动启动 ai-server，不需要手动编辑 .env 文件。** 所有模型配置通过浏览器完成。

---

## 环境要求

| 工具 | 版本建议 | 用途 |
|------|----------|------|
| **JDK** | 21（推荐）或 22～25 | 编译运行后端 |
| **Maven** | 3.9+ | 构建后端 |
| **Node.js** | 18+ / 20 LTS | 运行前端 |
| **MySQL** | 8.0+ | 业务数据库 |
| **Python** | 3.10+ | AI Server（后端可自动拉起） |

> 编译目标为 Java 21。若使用 JDK 25，需 Lombok 1.18.42+（已在 `pom.xml` 中配置）。

---

## 快速开始

### 1. 创建数据库

```sql
CREATE DATABASE IF NOT EXISTS agent
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
```

### 2. 修改数据库连接

编辑 `backend/src/main/resources/application.properties`：

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/agent?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=123456   # 改成你的 MySQL 密码
```

### 3. 启动后端

```powershell
cd backend
mvn spring-boot:run
```

成功标志：日志出现 `Started BackendApplication`，监听 **http://localhost:8080**。首次启动会自动：
- 执行 `init-tables.sql` 建 14 张业务表
- 创建测试账号 `admin` / `student1`（密码 `123456`）
- pip install ai-server 依赖 + 自动拉起 ai-server 进程（日志在 `backend/logs/ai-server.log`）

> 控制台菜单可忽略（输入 `quit` 不会停 Web 服务）。禁用：`app.console.enabled=false`。

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 **http://localhost:5173**。开发模式下 `/api`、`/ws` 代理到 `http://localhost:8080`。

### 5. 配置大模型 API Key（必做，启动后浏览器操作）

**ai-server 已自动拉起，只需配 Key：**

1. 浏览器打开 http://localhost:5173
2. 使用 `admin` / `123456` 登录 → 管理后台 → **系统配置**
3. 填写大模型提供商、模型名、API Key、API 地址
4. 勾选 **同步到 ai-server/.env**，保存
5. 点击 **测试大模型 API** 确认连通
6. 重启后端使 ai-server 加载新配置（或等热加载）

**完成**。现在用 `student1` / `123456` 登录就可以开始对话学习了。

> 不需要手动创建或编辑 ai-server/.env，后端会自动同步。

---

## 表结构如何生成

**推荐**：启动后端时自动执行 `backend/src/main/resources/db/init-tables.sql`（`spring.sql.init.mode=always`）。

**手动**（需先 `USE agent;`）：

```bash
mysql -u root -p agent < backend/src/main/resources/db/init-tables.sql
```

JPA 会为 `sys_user`、`kb_upload_task` 等实体同步字段；其余表由上述 SQL 脚本创建。

---

## 配置说明

| 配置类型 | 位置 |
|----------|------|
| 后端默认 | `backend/src/main/resources/application.properties` |
| 运行时覆盖 | MySQL `sys_config`（管理后台保存后优先） |
| 大模型 API | 管理后台 → 同步到 `ai-server/.env` |
| AI 服务地址 | `ai-server.base-url`（默认 `http://localhost:8001`） |
| **接口文档** | **http://localhost:8080/swagger-ui.html** |

常用 `sys_config` 键：`ai-server.base-url`、`ai-server.auto-start`、`llm.*`、`embedding.*`。

---

## 常见问题

**端口 8080 被占用**

```powershell
netstat -ano | findstr ":8080"
taskkill /PID <进程ID> /F
```

或修改 `server.port` 与 `frontend/vite.config.ts` 代理目标。

**编译报错 `TypeTag :: UNKNOWN`**

确认 `backend/pom.xml` 中 `lombok.version` 为 **1.18.42**，然后 `mvn clean compile`。

**登录失败**

使用 `admin` / `123456` 或 `student1` / `123456`；重启后端同步测试账号；检查 `sys_user` 表是否有数据。

**数据库只有 2 张表**

重启后端（会跑 `init-tables.sql`），或手动执行上文 SQL 脚本后刷新表列表。

**工作台显示「连接中…」**

检查后端是否启动（http://localhost:8080），以及 `.env` 中的 JWT 密钥是否配置正确。

**AI Server 启动失败**

查看 `backend/logs/ai-server.log`；确认 Python 3.10+ 与 `pip install -e ".[server]"`；检查 `.env` 中 LLM 配置。

**Windows 终端中文乱码**

原因：Java 用 UTF-8 输出，PowerShell 默认常为 GBK（代码页 936），菜单和日志会显示成乱码。

任选其一后**重新打开终端**再 `mvn spring-boot:run`：

1. 使用项目自带终端配置（推荐）：仓库内 `.vscode/settings.json` 已配置 UTF-8 PowerShell，在 Cursor/VS Code 里 **新建终端** 即可。
2. 手动执行：`chcp 65001`，再启动后端。
3. 不需要中文菜单时：在 `application.properties` 设 `app.console.enabled=false`（只保留英文日志前缀）。

---

## 当前功能完成度

### 核心架构

| 模块 | 状态 | 说明 |
|------|------|------|
| 登录 / 角色分流 | ✅ | admin→/admin, student1→/workspace |
| 管理后台 / 模型 API 配置 | ✅ | 浏览器配 Key，自动同步 ai-server/.env |
| 数据库表结构 | ✅ | 14 张业务表，启动时自动执行 DDL |
| ai-server 自动拉起 | ✅ | 后端启动时自动 pip install + 启动进程 |
| 学生工作台 UI 框架 | ✅ | 页面、路由、组件壳子、类型定义就绪 |
| **意图识别（Function Calling）** | ✅ | LLM 自动选择工具，支持 8 种意图（问候/帮助/学习路径/资源/问答/图表/总结/评估） |
| **ReAct QA Agent** | ✅ | 真正的 ReAct 循环，支持 RAG 搜索 |

### 多 Agent 组件

| 分类 | Agent | 状态 | 说明 |
|------|-------|------|------|
| **调度层** | Orchestrator | ✅ | Function Calling 意图识别 + Pipeline 调度 |
| | PlanningAgent | ✅ | LLM 任务拆解，支持 8 种意图路由 |
| **用户层** | ProfileAgent | ✅ | 用户画像分析 |
| | LearningStyleAgent | ✅ | 学习风格检测 |
| **学习层** | KnowledgeStateAgent | ✅ | 知识掌握度追踪 |
| | SpacedRepetitionAgent | ✅ | 间隔复习调度 |
| | DifficultyAgent | ✅ | 自适应难度调整 |
| | EffectAgent | ✅ | 学习效果评估 |
| **内容层** | QaAgent | ✅ | ReAct 问答（含 RAG） |
| | LessonAgent | ✅ | 讲义生成 |
| | DiagramAgent | ✅ | 图表生成 |
| | SummaryAgent | ✅ | 总结生成 |
| | PromptGeneration | ✅ | 提示词生成（自动判断图片/视频） |
| | MediaGeneration | ✅ | AI 图片/视频生成（Agnes AI） |
| | ContentAnalysisAgent | ✅ | 内容分析（LLM + 知识库） |
| **服务层** | RecommendationAgent | ✅ | 学习推荐 |
| | AssessmentAgent | ✅ | 评估批改 |
| | InterventionAgent | ✅ | 学习干预 |
| | QualityAgent | ✅ | 内容质量评估 |
| **基础层** | LearningPathService | ✅ | 学习路径规划 |

### 待开发功能

| 模块 | 优先级 | 说明 |
|------|--------|------|
| 学习路径 / 知识图谱可视化 | P1 | 图谱可视化展示 |
| 资源生成优化 | P1 | 讲义/题目/示意图增强 |
| 上传进知识库 | P3 | 文档上传 + 向量化 |

**统计：** 已实现 20 个 Agent 组件，覆盖用户画像、学习路径、智能问答、资源生成、媒体生成、效果评估等核心功能。

---

## 生产部署（简述）

1. 前端：`npm run build`，Nginx 托管 `dist/` 并反代 `/api`、`/ws` 到后端。  
2. 后端：`mvn package`，配置生产数据源与 JWT 密钥。  
3. 关闭 `app.console.enabled`；AI Server 独立进程守护，勿依赖开发模式自动拉起。

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/开发文档总结.md](./docs/开发文档总结.md) | 项目架构、Agent 设计、技术决策汇总 |

**接口文档**：启动后端后访问 **http://localhost:8080/swagger-ui.html**，所有接口在线可查可测。

数据库脚本唯一来源：`backend/src/main/resources/db/init-tables.sql`。
