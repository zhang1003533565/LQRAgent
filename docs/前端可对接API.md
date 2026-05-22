# 前端可对接 API 速查

> **给前端用**：哪些接口已经能调、怎么调。  
> 后端实现细节见 [后端开发进度](./后端开发进度.md)。  
> **最后核对**：2026-05-22

## 状态说明

| 标记 | 含义 |
|------|------|
| **可调** | 已实现，结构稳定，可直接对接 |
| **可调(占位)** | 能 200，但返回假数据或业务未接完 |
| **未实现** | 后端无 Handler，不要对接 |

## 通用约定

- **Base URL**：`/api`（Vite 代理到 `http://localhost:8080`）
- **鉴权**：除登录外，请求头 `Authorization: Bearer <token>`
- **响应包装**：

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

- **失败**：`code` 非 200 或 HTTP 4xx/5xx；`message` 为错误说明
- **401**：前端 `http.ts` 会自动登出并跳转 `/login`
- **前端封装**：`frontend/src/api/*.ts`（与下表一一对应）

### 测试账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `123456` | ADMIN → `/admin` |
| `student1` | `123456` | STUDENT → `/workspace` |

---

## 一、认证（无需 Token）

| 状态 | 方法 | 路径 | 前端封装 |
|------|------|------|----------|
| **可调** | POST | `/api/auth/login` | `api/auth.ts` → `login` |
| **可调** | POST | `/api/auth/logout` | `api/auth.ts` → `logout` |

### POST `/api/auth/login`

**Body (JSON)**

```json
{ "username": "student1", "password": "123456" }
```

**`data` 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `token` | string | JWT，后续请求带上 |
| `userId` | number | 用户 ID |
| `username` | string | 用户名 |
| `role` | string | `student` / `admin`（教师角色业务未做） |
| `redirectPath` | string | 建议跳转：`/workspace` 或 `/admin` |

### POST `/api/auth/logout`

无 Body。JWT 无状态，客户端丢弃 token 即可。

---

## 二、用户（需登录）

| 状态 | 方法 | 路径 | 角色 | 前端封装 |
|------|------|------|------|----------|
| **可调** | GET | `/api/users/me` | 任意已登录 | `api/user.ts` |

**`data` 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 用户 ID |
| `username` | string | 用户名 |
| `displayName` | string | 显示名 |
| `role` | string | 角色 |

> 暂无画像字段；画像 API 未实现。

---

## 三、学生端 — 上传队列（需登录，STUDENT/ADMIN）

| 状态 | 方法 | 路径 | 前端封装 |
|------|------|------|----------|
| **可调** | POST | `/api/upload` | `api/upload.ts` → `uploadFile` |
| **可调** | GET | `/api/upload/tasks` | `api/upload.ts` → `listUploadTasks` |

### POST `/api/upload`

**Content-Type**：`multipart/form-data`

| 字段 | 必填 | 说明 |
|------|------|------|
| `file` | 是 | 上传文件 |
| `scope` | 否 | `PERSONAL`（默认）或 `PUBLIC` |

**`data`**：`KbUploadTask` 对象（见下表）

> **注意**：worker 尚未调用 ai-server，任务可能很快变为 `COMPLETED`，但**未真正写入知识库**。

### GET `/api/upload/tasks`

返回当前登录用户的任务列表。

**`KbUploadTask` 主要字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | number | 任务 ID |
| `userId` | number | 用户 ID |
| `fileName` | string | 文件名 |
| `kbScope` | string | `PERSONAL` / `PUBLIC` |
| `status` | string | `PENDING` / `PROCESSING` / `COMPLETED` / `FAILED` |
| `errorMessage` | string? | 失败原因 |
| `createdAt` | string | ISO 时间 |
| `startedAt` / `finishedAt` | string? | 处理时间 |

---

## 四、学生端 — 学习路径（需登录）

| 状态 | 方法 | 路径 | 前端封装 |
|------|------|------|----------|
| **可调(占位)** | GET | `/api/learning-path` | `api/learningPath.ts` → `getLearningPath` |

### GET `/api/learning-path`

**Query**

| 参数 | 必填 | 说明 |
|------|------|------|
| `goal` | 是 | 学习目标，如「学习 Python 装饰器」 |
| `currentKpId` | 否 | 当前知识点 ID |

**`data` 字段**

| 字段 | 类型 | 说明 |
|------|------|------|
| `goal` | string | 回显目标 |
| `nodes` | array | 路径节点，按 `order` 排序 |
| `nodes[].kpId` | string | 知识点 ID |
| `nodes[].title` | string | 标题 |
| `nodes[].description` | string | 描述 |
| `nodes[].order` | number | 顺序 |
| `nodes[].completed` | boolean | 是否已完成 |
| `planDescription` | string | 自然语言学习计划 |

> **占位说明**：当前固定返回 `kp_placeholder_1`，未接知识图谱与 AI。UI 可对接联调，但不要按真实路径业务验收。

### 未实现（规划对接）

| 路径 | 说明 |
|------|------|
| `GET /api/learning-path/current` | 当前活跃路径 |
| `POST /api/learning-path/generate` | 生成/刷新路径 |
| `POST /api/quiz/submit` | 提交答题 |
| `GET /api/profile/summary` | 画像摘要 |

### POST `/api/resources/generate`（规划）

**Body (JSON)**

```json
{
  "kpId": "kp_decorator",
  "type": "LESSON",
  "prompt": "可选，覆盖默认生成提示词"
}
```

**`type` 枚举**

| type | 说明 | 阶段 |
|------|------|------|
| `LESSON` | 讲义 Markdown | P1 |
| `QUIZ` | 题目 JSON | P1 |
| `CODE_CASE` | 代码案例 | P1 |
| `ILLUSTRATION` | **生图**，返回 `mediaUrl` + `mediaMime` | P1 |
| `VIDEO_CLIP` | **生视频**，返回 `mediaUrl` + `mediaMime` | P4 |

**`data` 响应（媒体类示例）**

```json
{
  "id": 1,
  "kpId": "kp_decorator",
  "resourceType": "ILLUSTRATION",
  "title": "装饰器调用示意",
  "content": "配图说明 Markdown（可选）",
  "mediaUrl": "/api/media/1",
  "mediaMime": "image/png",
  "generationPrompt": "..."
}
```

前端：`ResourceTabs` 图片/视频 Tab；`MediaPreview` 组件。

---

## 五、管理后台（需登录，角色 ADMIN）

| 状态 | 方法 | 路径 | 前端封装 |
|------|------|------|----------|
| **可调** | GET | `/api/admin/status` | `api/admin.ts` → `getAdminStatus` |
| **可调** | GET | `/api/admin/model-config` | `getModelConfig` |
| **可调** | PUT | `/api/admin/model-config` | `saveModelConfig` |
| **可调** | POST | `/api/admin/model-config/test-llm` | `testLlmConfig` |
| **可调** | GET | `/api/admin/config` | `listSysConfig` |
| **可调** | PUT | `/api/admin/config/{key}` | `saveSysConfig` |
| **可调** | DELETE | `/api/admin/config/{key}` | `deleteSysConfig` |
| **可调** | POST | `/api/admin/ai/ping` | `pingAiServer` |
| **可调** | GET | `/api/admin/users` | `listAdminUsers` |
| **可调** | GET | `/api/admin/upload/tasks` | `listAdminUploadTasks` |
| **可调** | POST | `/api/admin/upload/process` | `processOneUpload` |

### 常用说明

- **PUT `/api/admin/config/{key}`** Body：`{ "configValue": "...", "remark": "可选" }`
- **GET `/api/admin/upload/tasks`** Query：`limit`（默认 50，最大 200）
- **POST `/api/admin/upload/process`**：`data.processed` 表示是否处理了一条待处理任务
- **POST `/api/admin/ai/ping`**：`data.reachable`、`data.baseUrl`

### 未实现（勿调）

| 路径 | 说明 |
|------|------|
| `GET /api/admin/agent-stats` | 智能体统计 |
| `GET /api/admin/agent-runs` | 智能体运行日志 |
| `GET /api/admin/users/{id}/profile-summary` | 学生画像摘要 |
| `PUT /api/admin/config/media.image.*` 等 | 生图/生视频 API 配置（规划，P4） |

---

## 六、WebSocket（未实现）

| 状态 | 端点 | 说明 |
|------|------|------|
| **未实现** | `WS /ws/chat?token=<JWT>` | 前端 `useWebSocket` 已写，**后端无 Handler** |

### 服务端事件（含多模态，实现后扩展 `useWebSocket`）

| type | 说明 |
|------|------|
| `chunk` | 流式文本 |
| `agent_step` | 智能体步骤 |
| `artifact` | 结构化结果；`kind` 含 `learning_path`、`lesson`、`quiz`、`media_image`、`media_video`、`multi_card` |
| `profile_patch` | 画像增量 |
| `done` / `error` | 结束 / 错误 |

`artifact` 示例（生图完成）：

```json
{
  "type": "artifact",
  "kind": "media_image",
  "payload": {
    "url": "/api/media/12",
    "mime": "image/png",
    "title": "列表推导式示意",
    "kpId": "kp_list_comp"
  }
}
```

事件协议详见 [项目精简开发指南 § 三、§8.1](./项目精简开发指南.md)。

---

## 相关文档

- [文档索引](./文档索引.md)
- [前端功能模块](./前端功能模块.md) — 页面与组件清单（不看接口时先看这份）
- [后端开发进度](./后端开发进度.md) — 模块级 ✅/🟡/❌
- [项目精简开发指南](./项目精简开发指南.md) — 架构与 WS 事件协议

**维护**：后端新增或改接口后，同步改本文状态列与请求/响应说明。
