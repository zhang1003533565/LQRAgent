# LQRAgent

面向《Python 高级语言程序设计》的个性化 AI 学习系统（软件杯）。

```
浏览器 → frontend:5173 → 代理 /api、/ws → backend:8080 → ai-server:8001
```

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8080/api/... |
| AI Server | http://localhost:8001 |
| 管理后台 | 登录 `admin` → `/admin` |

默认账号（密码均为 **123456**）：`admin`（管理员）、`student1`（学生）。

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

成功标志：日志出现 `Started BackendApplication`，监听 **http://localhost:8080**。首次启动会自动执行 `backend/src/main/resources/db/init-tables.sql` 建齐约 14 张业务表，并创建测试账号。

可选：同一终端出现 `LQRAgent>` 时可输入 `help` 管理配置；关闭菜单输入 `quit` 不会停止 Web。禁用控制台：`app.console.enabled=false`。

### 4. 启动前端

```powershell
cd frontend
npm install
npm run dev
```

浏览器访问 **http://localhost:5173**。开发模式下 `/api`、`/ws` 代理到 `http://localhost:8080`。

### 5. 配置大模型 API（必做）

对话、RAG、资源生成依赖 ai-server 中的大模型配置。

**方式 A — 管理后台（推荐）**

1. 使用 `admin` / `123456` 登录 → **系统配置**
2. 填写大模型提供商、模型名、API Key、API 地址
3. 勾选 **同步到 ai-server/.env**，保存并 **测试大模型 API**
4. **重启后端**（或 AI 进程）使 `.env` 生效

**方式 B — 直接编辑 `ai-server/.env`**

```powershell
cd ai-server
copy .env.example .env    # Windows
# cp .env.example .env    # macOS / Linux
```

至少配置 `LLM_BINDING`、`LLM_MODEL`、`LLM_API_KEY`、`LLM_HOST` 及对应的 `EMBEDDING_*`。

### 6. 单独启动 AI Server（可选）

若 `ai-server.auto-start=false`：

```powershell
cd ai-server
python -m pip install -e ".[server]"
python -m deeptutor.api.run_server
```

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

`/ws/chat` 尚未实现，属已知待开发项；登录与管理后台不受影响。

**AI Server 启动失败**

查看 `backend/logs/ai-server.log`；确认 Python 3.10+ 与 `pip install -e ".[server]"`；检查 `.env` 中 LLM 配置。

**Windows 终端中文乱码**

原因：Java 用 UTF-8 输出，PowerShell 默认常为 GBK（代码页 936），菜单和日志会显示成乱码。

任选其一后**重新打开终端**再 `mvn spring-boot:run`：

1. 使用项目自带终端配置（推荐）：仓库内 `.vscode/settings.json` 已配置 UTF-8 PowerShell，在 Cursor/VS Code 里 **新建终端** 即可。
2. 手动执行：`chcp 65001`，再启动后端。
3. 不需要中文菜单时：在 `application.properties` 设 `app.console.enabled=false`（只保留英文日志前缀）。

---

## 当前功能完成度（简要）

| 模块 | 状态 |
|------|------|
| 登录 / 角色分流 | ✅ |
| 管理后台 / 系统配置 / 模型 API | ✅ |
| 数据库表结构 | ✅ |
| 学生工作台 UI | ✅ 页面可用 |
| 流式对话 | ⏳ WebSocket 待实现 |
| 学习路径 / 知识图谱 | ⏳ 占位数据 |
| 上传进知识库 | ⏳ 队列有，对接 AI 待实现 |
| 多模态资源（生图 / 生视频） | ⏳ 已纳入规划（P1 示意图，P4 短视频） |

多模态设计见 [docs/项目精简开发指南.md §8.1](./docs/项目精简开发指南.md)。

---

## 生产部署（简述）

1. 前端：`npm run build`，Nginx 托管 `dist/` 并反代 `/api`、`/ws` 到后端。  
2. 后端：`mvn package`，配置生产数据源与 JWT 密钥。  
3. 关闭 `app.console.enabled`；AI Server 独立进程守护，勿依赖开发模式自动拉起。

---

## 文档

| 文档 | 说明 |
|------|------|
| [docs/文档索引.md](./docs/文档索引.md) | 文档入口 |
| [docs/前端功能模块.md](./docs/前端功能模块.md) | **学生工作台**功能模块（管理后台 UI 自研，见后端进度） |
| [docs/前端可对接API.md](./docs/前端可对接API.md) | 已写通接口速查（给前端） |
| [docs/项目精简开发指南.md](./docs/项目精简开发指南.md) | 架构、事件协议、P0～P4 |
| [docs/后端开发进度.md](./docs/后端开发进度.md) | 后端 ✅/🟡/❌ 对照 |

数据库脚本唯一来源：`backend/src/main/resources/db/init-tables.sql`。
