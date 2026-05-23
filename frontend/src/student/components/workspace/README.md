# 学生工作台 (`features/workspace`)

与 DeepTutor `ai-server/web` 一致：**侧栏 + 主区** 两栏，无第三栏。

| 目录 | 职责 |
|------|------|
| `shell/` | 布局壳、侧栏导航 |
| `chat/` | 对话流、Composer、Agent 步骤条 |
| `context/` | 主区 Tab：路径、资源、练习 |
| `upload/` | 上传资料页 |
| `profile/` | 侧栏用户卡、画像详情页 |

入口：`pages/WorkspacePage.tsx` → `WorkspaceShell` + 子路由。
